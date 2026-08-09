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

    public List<KitchenOrderResponse> getKitchenOrders(String status, Long branchId, Long tableId, UUID merchantId) {
        List<OrderEntity> orders;

        if (branchId != null && status != null && !status.isBlank()) {
            orders = orderRepository.findByBranchIdAndStatus(branchId, status.toUpperCase());
        } else if (merchantId != null && status != null && !status.isBlank()) {
            orders = orderRepository.findByMerchantIdAndStatus(merchantId, status.toUpperCase());
        } else if (tableId != null) {
            orders = orderRepository.findByTableId(tableId);
        } else if (branchId != null) {
            orders = orderRepository.findByBranchId(branchId);
        } else if (merchantId != null) {
            orders = orderRepository.findByMerchantId(merchantId);
        } else {
            orders = orderRepository.findAll();
        }

        return orders.stream().map(order -> {
            String tableNum = fetchTableNumber(order.getTableId());

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
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

    private String fetchTableNumber(Long tableId) {
        try {
            String url = merchantServiceUrl + "/api/tables/" + tableId;
            // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return "T-01";
            }
            return body.get("tableNumber") != null ? (String) body.get("tableNumber") : "T-01";
        } catch (Exception e) {
            log.warn("Failed to fetch table {} from merchant-service: {}", tableId, e.getMessage());
            return "T-01";
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