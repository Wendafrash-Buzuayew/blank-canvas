package com.qrserve.order.service;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.CreateOrderResponse;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.entity.OrderItemEntity;
import com.qrserve.order.repository.OrderItemRepository;
import com.qrserve.order.repository.OrderRepository;
import com.qrserve.shared.common.OrderStatus;
import com.qrserve.shared.common.TableStatus;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.security.JwtTokenProvider;

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
import com.qrserve.shared.exceptions.ServiceUnavailableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
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
    private final JwtTokenProvider tokenProvider;

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
                .status(OrderStatus.PENDING.name())
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
        updateTableStatus(table.getId(), TableStatus.OCCUPIED.name());

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
                // Issued to every caller, authenticated or not: the guest who placed
                // the order needs it to watch /topic/orders/{id}, and it grants
                // nothing beyond that one order.
                .streamToken(tokenProvider.generateOrderStreamToken(savedOrder.getId()))
                .build();
    }

    @Transactional
    public OrderEntity updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found ID: " + orderId));

        String previousStatus = order.getStatus();
        OrderStatus next = request.getStatus();

        // Reject illegal transitions. Binding to the enum stops unknown names, but
        // PENDING -> PAID is a well-formed request that still corrupts reporting, so
        // the ordering has to be checked too.
        OrderStatus current;
        try {
            current = OrderStatus.valueOf(previousStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            // A row persisted before the enum existed may hold an unknown value.
            // Allow the correction rather than trapping the order forever, but say so.
            log.warn("Order {} holds unrecognised status '{}'; allowing transition to {}",
                    orderId, previousStatus, next);
            current = null;
        }
        if (current != null && current != next && !current.canTransitionTo(next)) {
            throw new BusinessException(
                    "Cannot move order from " + current + " to " + next
                            + "; allowed: " + current.allowedNext());
        }

        order.setStatus(next.name());

        if (next == OrderStatus.DELIVERED || next == OrderStatus.PAID) {
            updateTableStatus(order.getTableId(), TableStatus.AVAILABLE.name());
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

    /**
     * Fetches a table from merchant-service.
     *
     * <p>Every distinct failure used to collapse into {@code ResourceNotFoundException
     * ("Table not found ID: n")} — a genuine 404, a connection refused, a 403, a 500
     * and a response-mapping bug were indistinguishable to the caller, and the text
     * was byte-identical to what merchant-service itself emits for a real 404. That
     * made the failure undiagnosable from the API response alone.
     *
     * <p>Now: 404 means absent, 4xx/5xx and transport errors mean unavailable, and a
     * malformed payload says so. The downstream URL and status are logged in all
     * cases and the cause is preserved.
     */
    private TableInfo fetchTable(Long tableId) {
        String url = merchantServiceUrl + "/api/tables/" + tableId;
        Map<String, Object> body;
        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("merchant-service reports table {} does not exist ({})", tableId, url);
            throw new ResourceNotFoundException("Table not found ID: " + tableId);
        } catch (HttpStatusCodeException e) {
            // 401/403 here almost always means the inbound end-user token was absent
            // or unprivileged — an anonymous order placement forwards no token.
            log.error("merchant-service returned {} for {} — body: {}",
                    e.getStatusCode(), url, e.getResponseBodyAsString());
            throw new ServiceUnavailableException(
                    "merchant-service returned " + e.getStatusCode() + " while resolving the table", e);
        } catch (RestClientException e) {
            log.error("merchant-service unreachable at {}", url, e);
            throw new ServiceUnavailableException("merchant-service is unreachable", e);
        }

        if (body == null) {
            log.error("merchant-service returned an empty body for {}", url);
            throw new ServiceUnavailableException("merchant-service returned an empty table payload");
        }

        try {
            return new TableInfo(
                    ((Number) body.get("id")).longValue(),
                    ((Number) body.get("branchId")).longValue(),
                    UUID.fromString((String) body.get("merchantId")),
                    (String) body.get("tableNumber")
            );
        } catch (NullPointerException | ClassCastException | IllegalArgumentException e) {
            // A row with a null branch_id / merchant_id, or a contract change, used to
            // surface as "table not found" — which sent you looking in the wrong place.
            log.error("Unexpected table payload from {}: {}", url, body, e);
            throw new ServiceUnavailableException(
                    "merchant-service returned an unexpected table payload for ID " + tableId, e);
        }
    }

    /** See {@link #fetchTable} — same masking problem, same treatment. */
    private ProductInfo fetchProduct(Long productId) {
        String url = menuServiceUrl + "/api/products/" + productId;
        Map<String, Object> body;
        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("menu-service reports product {} does not exist ({})", productId, url);
            throw new ResourceNotFoundException("Product not found ID: " + productId);
        } catch (HttpStatusCodeException e) {
            log.error("menu-service returned {} for {} — body: {}",
                    e.getStatusCode(), url, e.getResponseBodyAsString());
            throw new ServiceUnavailableException(
                    "menu-service returned " + e.getStatusCode() + " while resolving a product", e);
        } catch (RestClientException e) {
            log.error("menu-service unreachable at {}", url, e);
            throw new ServiceUnavailableException("menu-service is unreachable", e);
        }

        if (body == null) {
            log.error("menu-service returned an empty body for {}", url);
            throw new ServiceUnavailableException("menu-service returned an empty product payload");
        }

        try {
            return new ProductInfo(
                    ((Number) body.get("id")).longValue(),
                    (String) body.get("name"),
                    new BigDecimal(body.get("price").toString()),
                    body.get("preparationTime") != null ? ((Number) body.get("preparationTime")).intValue() : 15
            );
        } catch (NullPointerException | ClassCastException | IllegalArgumentException e) {
            log.error("Unexpected product payload from {}: {}", url, body, e);
            throw new ServiceUnavailableException(
                    "menu-service returned an unexpected product payload for ID " + productId, e);
        }
    }

    /**
     * Best-effort table occupancy update, with a bounded retry.
     *
     * <p>Deliberately does not fail the order: a customer's order must not be
     * rejected because a table flag could not be flipped. But the previous version
     * logged only a warning, so three separate faults were invisible — PATCH was
     * unsupported by the default request factory, no Authorization header was
     * forwarded, and any failure was discarded. Exhausted retries now log at ERROR
     * with the status so monitoring can alert on occupancy drift.
     *
     * <p>KNOWN LIMITATION: an order placed by an anonymous guest forwards no token,
     * so this call is rejected by the endpoint's role check. That is the
     * inter-service identity gap tracked as deferred work — it needs a service
     * credential, not another retry.
     */
    private void updateTableStatus(Long tableId, String status) {
        String url = merchantServiceUrl + "/api/tables/" + tableId + "/status";
        HttpEntity<Map<String, String>> requestEntity =
                new HttpEntity<>(Map.of("status", status), getAuthHeaders());

        long backoffMs = 100L;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, Map.class);
                return;
            } catch (HttpClientErrorException e) {
                // 4xx will not become 3xx on retry — stop immediately.
                log.error("Table {} status not set to {}: merchant-service rejected the call with {} ({})",
                        tableId, status, e.getStatusCode(), url);
                return;
            } catch (RestClientException e) {
                if (attempt == 3) {
                    log.error("Table {} status not set to {} after {} attempts; occupancy is now stale",
                            tableId, status, attempt, e);
                    return;
                }
                log.warn("Attempt {}/3 to set table {} status to {} failed: {}",
                        attempt, tableId, status, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMs *= 2;
            }
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