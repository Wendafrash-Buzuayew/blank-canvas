package com.qrserve.order.service;

import com.qrserve.order.dto.KitchenOrderResponse;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.entity.OrderItemEntity;
import com.qrserve.order.repository.OrderItemRepository;
import com.qrserve.order.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest; // Fixed: Added Jakarta Servlet import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder; // Fixed: Added Web Context import
import org.springframework.web.context.request.ServletRequestAttributes; // Fixed: Added Web Attributes import

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RestTemplate restTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    /**
     * Lists kitchen orders within one tenant.
     *
     * @param merchantId the caller's tenant scope, resolved by the controller from
     *                   the token. Null only for SUPER_ADMIN reading across tenants.
     *                   branchId/tableId narrow within it and can never widen it —
     *                   they used to be queried on their own, returning any
     *                   tenant's orders for a guessed branch or table id.
     */
    public List<KitchenOrderResponse> getKitchenOrders(String status, Long branchId, Long tableId, UUID merchantId) {
        String wantedStatus = status != null && !status.isBlank() ? status.toUpperCase() : null;
        List<OrderEntity> orders;

        if (merchantId != null) {
            orders = wantedStatus != null
                    ? orderRepository.findByMerchantIdAndStatus(merchantId, wantedStatus)
                    : orderRepository.findByMerchantId(merchantId);
        } else {
            orders = wantedStatus != null
                    ? orderRepository.findByStatus(wantedStatus)
                    : orderRepository.findAll();
        }

        orders = orders.stream()
                .filter(o -> branchId == null || branchId.equals(o.getBranchId()))
                .filter(o -> tableId == null || tableId.equals(o.getTableId()))
                .collect(Collectors.toList());

        // One item query for the whole board instead of one per order, and one
        // table lookup per distinct table instead of one per order.
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orders.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                        .stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Map<Long, String> tableNumbers = new HashMap<>();

        return orders.stream().map(order -> {
            String tableNum = tableNumbers.computeIfAbsent(order.getTableId(), this::fetchTableNumber);

            List<OrderItemEntity> items = itemsByOrder.getOrDefault(order.getId(), List.of());
            List<KitchenOrderResponse.ItemDto> itemDtos = items.stream().map(item ->
                    KitchenOrderResponse.ItemDto.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getSubtotal())
                            .notes(item.getNotes())
                            .build()
            ).collect(Collectors.toList());

            return KitchenOrderResponse.builder()
                    .id(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .merchantId(order.getMerchantId())
                    .branchId(order.getBranchId())
                    .tableId(order.getTableId())
                    .tableNumber(tableNum)
                    .customerName(order.getCustomerName())
                    .status(order.getStatus())
                    .totalAmount(order.getTotalAmount())
                    .note(order.getNote())
                    .createdAt(order.getCreatedAt())
                    .items(itemDtos)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Returns null when the table number cannot be resolved. It used to return a
     * made-up "T-01", which sent food to the wrong table on a lookup failure.
     * (HashMap.computeIfAbsent does not cache a null, so a failed lookup is retried
     * for the next order on the same table.)
     */
    private String fetchTableNumber(Long tableId) {
        try {
            String url = merchantServiceUrl + "/api/tables/" + tableId;
            // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }
            return body.get("tableNumber") != null ? (String) body.get("tableNumber") : null;
        } catch (Exception e) {
            log.warn("Failed to fetch table {} from merchant-service: {}", tableId, e.getMessage());
            return null;
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
}