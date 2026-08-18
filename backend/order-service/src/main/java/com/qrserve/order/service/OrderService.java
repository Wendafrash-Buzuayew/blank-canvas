package com.qrserve.order.service;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.CreateOrderResponse;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.entity.OrderItemEntity;
import com.qrserve.order.repository.OrderItemRepository;
import com.qrserve.order.repository.OrderRepository;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import com.qrserve.shared.exceptions.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    @Value("${services.menu-service-url:http://localhost:8086}")
    private String menuServiceUrl;

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        TableInfo table = fetchTable(request.getTableId());

        String orderNumber = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        
        OrderEntity order = OrderEntity.builder()
                .orderNumber(orderNumber)
                .merchantId(table.getMerchantId())
                .branchId(table.getBranchId())
                .tableId(table.getId())
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : "Guest")
                .status("PENDING")
                .totalAmount(BigDecimal.ZERO)
                .note(request.getNote())
                .build();

        OrderEntity savedOrder = orderRepository.save(order);

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        int maxPrepTime = 10;
        List<OrderItemEntity> orderItems = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductInfo product = fetchProduct(itemReq.getProductId());

            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            calculatedTotal = calculatedTotal.add(itemSubtotal);

            if (product.getPreparationTime() != null && product.getPreparationTime() > maxPrepTime) {
                maxPrepTime = product.getPreparationTime();
            }

            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .orderId(savedOrder.getId())
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(itemSubtotal)
                    .notes(itemReq.getNotes())
                    .build();

            orderItems.add(orderItem);
        }

        orderItemRepository.saveAll(orderItems);

        savedOrder.setTotalAmount(calculatedTotal);
        orderRepository.save(savedOrder);

        // Mark table status as OCCUPIED via merchant-service REST call
        updateTableStatus(table.getId(), "OCCUPIED");

        // Publish Kafka event for notification/analytics services
        eventPublisher.publishOrderCreated(OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .merchantId(savedOrder.getMerchantId())
                .branchId(savedOrder.getBranchId())
                .tableId(savedOrder.getTableId())
                .tableNumber(table.getTableNumber())
                .customerName(savedOrder.getCustomerName())
                .totalAmount(calculatedTotal)
                .note(savedOrder.getNote())
                .createdAt(savedOrder.getCreatedAt())
                .build());

        return CreateOrderResponse.builder()
                .id(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .status(savedOrder.getStatus())
                .estimatedTime(maxPrepTime)
                .totalAmount(calculatedTotal)
                .build();
    }

    @Transactional
    public OrderEntity updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found ID: " + orderId));

        String previousStatus = order.getStatus();
        order.setStatus(request.getStatus().toUpperCase());

        if ("DELIVERED".equalsIgnoreCase(request.getStatus()) || "PAID".equalsIgnoreCase(request.getStatus())) {
            updateTableStatus(order.getTableId(), "AVAILABLE");
        }

        OrderEntity updated = orderRepository.save(order);

        // Publish Kafka event for notification/analytics services
        eventPublisher.publishOrderStatusUpdated(OrderStatusUpdatedEvent.builder()
                .orderId(updated.getId())
                .orderNumber(updated.getOrderNumber())
                .merchantId(updated.getMerchantId())
                .branchId(updated.getBranchId())
                .tableId(updated.getTableId())
                .previousStatus(previousStatus)
                .newStatus(updated.getStatus())
                .build());

        return updated;
    }

    /**
     * Lists orders, scoped to a merchant when one is supplied.
     *
     * <p>A null merchantId returns every order and is reserved for SUPER_ADMIN.
     * Filtering is done in the query so another tenant's rows never leave the
     * database — analytics-service previously fetched all orders and computed
     * global revenue for every merchant.
     */
    public List<OrderEntity> getAllOrders(UUID merchantId) {
        return merchantId != null
                ? orderRepository.findByMerchantId(merchantId)
                : orderRepository.findAll();
    }

    public OrderEntity getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found ID: " + orderId));
    }

    private TableInfo fetchTable(Long tableId) {
        try {
            String url = merchantServiceUrl + "/api/tables/" + tableId;
             // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Table not found ID: " + tableId);
            }
            
            return new TableInfo(
                    ((Number) body.get("id")).longValue(),
                    ((Number) body.get("branchId")).longValue(),
                    UUID.fromString((String) body.get("merchantId")),
                    (String) body.get("tableNumber")
            );
        } catch (Exception e) {
            log.error("Failed to fetch table {} from merchant-service", tableId, e);
            throw new ResourceNotFoundException("Table not found ID: " + tableId);
        }
    }

    private ProductInfo fetchProduct(Long productId) {
        try {
            String url = menuServiceUrl + "/api/products/" + productId;
             // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Product not found ID: " + productId);
            }
            
            return new ProductInfo(
                    ((Number) body.get("id")).longValue(),
                    (String) body.get("name"),
                    new BigDecimal(body.get("price").toString()),
                    body.get("preparationTime") != null ? ((Number) body.get("preparationTime")).intValue() : 15
            );
        } catch (Exception e) {
            log.error("Failed to fetch product {} from menu-service", productId, e);
            throw new ResourceNotFoundException("Product not found ID: " + productId);
        }
    }

    private void updateTableStatus(Long tableId, String status) {
        try {
            String url = merchantServiceUrl + "/api/tables/" + tableId + "/status";
            Map<String, String> body = Map.of("status", status);
            restTemplate.patchForObject(url, body, Map.class);
        } catch (Exception e) {
            log.warn("Failed to update table {} status to {}: {}", tableId, status, e.getMessage());
        }
    }

    /**
     * Extracts Authorization Header from the current request thread
     */
    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, authToken);
            }
        }
        return headers;
    }

    private static class TableInfo {
        private final Long id;
        private final Long branchId;
        private final UUID merchantId;
        private final String tableNumber;

        TableInfo(Long id, Long branchId, UUID merchantId, String tableNumber) {
            this.id = id;
            this.branchId = branchId;
            this.merchantId = merchantId;
            this.tableNumber = tableNumber;
        }

        public Long getId() { return id; }
        public Long getBranchId() { return branchId; }
        public UUID getMerchantId() { return merchantId; }
        public String getTableNumber() { return tableNumber; }
    }

    private static class ProductInfo {
        private final Long id;
        private final String name;
        private final BigDecimal price;
        private final Integer preparationTime;

        ProductInfo(Long id, String name, BigDecimal price, Integer preparationTime) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.preparationTime = preparationTime;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        public Integer getPreparationTime() { return preparationTime; }
    }
}