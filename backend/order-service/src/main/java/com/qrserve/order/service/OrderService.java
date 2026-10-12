package com.qrserve.order.service;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.CreateOrderResponse;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.entity.OrderItemEntity;
import com.qrserve.order.repository.OrderItemRepository;
import com.qrserve.order.repository.OrderRepository;
import com.qrserve.shared.common.OrderStatus;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TableStatus;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /**
     * Statuses after which an order no longer occupies its table. DELIVERED is
     * included, as before: pay-at-counter orders may never be marked PAID.
     */
    static final Set<OrderStatus> TABLE_RELEASING =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.PAID, OrderStatus.CANCELLED);

    /** Statuses of an order still keeping its table occupied. */
    static final List<String> OPEN_STATUSES = EnumSet.complementOf(EnumSet.copyOf(TABLE_RELEASING))
            .stream().map(Enum::name).toList();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventPublisher eventPublisher;
    private final RestTemplate restTemplate;
    private final JwtTokenProvider tokenProvider;
    private final QrSignatureService qrSignatureService;
    private final TransactionTemplate transactionTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    @Value("${services.menu-service-url:http://localhost:8086}")
    private String menuServiceUrl;

    /**
     * Places an order.
     *
     * <p>Not {@code @Transactional}: the merchant/menu REST calls happen before the
     * transaction and the table PATCH + Kafka publish after it commits. Holding a
     * pooled connection (pool size 5) across REST retries starved the pool, and an
     * event published before commit could announce an order that then rolled back.
     *
     * <p>Idempotency keys for guest retries are NOT implemented here.
     */
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        TableInfo table = fetchTable(request.getTableId());

        // The signature proves the guest scanned this table's printed QR code.
        // Without it, anyone could place orders against any tableId of any tenant.
        if (!qrSignatureService.validateSignature(request.getSignature(),
                table.getMerchantId(), table.getBranchId(), table.getId())) {
            throw new AccessDeniedException("Invalid QR signature for the requested table");
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        int maxPrepTime = 10;
        List<OrderItemEntity> orderItems = new ArrayList<>();

        // One menu fetch for the whole order instead of a REST call per line item.
        // GET /api/menu/{merchantId} is already a public endpoint, so this also
        // removes the need for order-service to authenticate against
        // GET /api/products/{id} — a protected endpoint it was calling with no
        // token, which is what surfaced as a 503 to the customer.
        Map<Long, ProductInfo> menu = indexMenu(fetchMenu(table.getMerchantId()));

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductInfo product = resolveProduct(menu, itemReq.getProductId());

            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            calculatedTotal = calculatedTotal.add(itemSubtotal);

            if (product.getPreparationTime() != null && product.getPreparationTime() > maxPrepTime) {
                maxPrepTime = product.getPreparationTime();
            }

            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(itemSubtotal)
                    .notes(itemReq.getNotes())
                    .build();

            orderItems.add(orderItem);
        }

        String orderNumber = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        OrderEntity order = OrderEntity.builder()
                .orderNumber(orderNumber)
                .merchantId(table.getMerchantId())
                .branchId(table.getBranchId())
                .tableId(table.getId())
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : "Guest")
                .status(OrderStatus.PENDING.name())
                .totalAmount(calculatedTotal)
                .note(request.getNote())
                .build();

        OrderEntity savedOrder = transactionTemplate.execute(status -> {
            OrderEntity saved = orderRepository.save(order);
            orderItems.forEach(item -> item.setOrderId(saved.getId()));
            orderItemRepository.saveAll(orderItems);
            return saved;
        });

        // --- committed; everything below is outside the transaction ---

        // Mark table status as OCCUPIED via merchant-service REST call
        boolean tableUpdated = updateTableStatus(table.getId(), TableStatus.OCCUPIED.name());

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
                .tableStatusUpdated(tableUpdated)
                .build();
    }

    /**
     * Changes an order's status, scoped to the caller's tenant.
     *
     * <p>The DB write commits first; the table release and the Kafka event happen
     * only after commit, so a rolled-back or conflicting (409) update never
     * announces a status that was not persisted.
     */
    public OrderEntity updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request, UserPrincipal principal) {
        OrderStatus next = request.getStatus();
        String[] previous = new String[1];

        OrderEntity updated = transactionTemplate.execute(txStatus -> {
            OrderEntity order = getOrder(orderId, principal);
            previous[0] = order.getStatus();
            applyTransition(order, next);
            // Flush now so a concurrent update surfaces as an optimistic-lock
            // failure (409) here rather than at commit.
            return orderRepository.saveAndFlush(order);
        });

        // --- committed; everything below is outside the transaction ---

        if (TABLE_RELEASING.contains(next)) {
            releaseTableIfIdle(updated);
        }

        // Publish Kafka event for notification/analytics services
        eventPublisher.publishOrderStatusUpdated(OrderStatusUpdatedEvent.builder()
                .orderId(updated.getId())
                .orderNumber(updated.getOrderNumber())
                .merchantId(updated.getMerchantId())
                .branchId(updated.getBranchId())
                .tableId(updated.getTableId())
                .previousStatus(previous[0])
                .newStatus(updated.getStatus())
                .build());

        return updated;
    }

    /**
     * Frees the table only when no other order on it is still open. A table with
     * two orders used to be freed when the first was delivered; a cancelled order
     * never freed it at all.
     *
     * @return true if the table was marked AVAILABLE
     */
    boolean releaseTableIfIdle(OrderEntity order) {
        if (orderRepository.existsByTableIdAndIdNotAndStatusIn(
                order.getTableId(), order.getId(), OPEN_STATUSES)) {
            log.info("Table {} stays occupied: another open order remains after order {} became {}",
                    order.getTableId(), order.getOrderNumber(), order.getStatus());
            return false;
        }
        return updateTableStatus(order.getTableId(), TableStatus.AVAILABLE.name());
    }

    private void applyTransition(OrderEntity order, OrderStatus next) {
        UUID orderId = order.getId();
        String previousStatus = order.getStatus();

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
        if (next == OrderStatus.PAID) {
            order.setPaymentStatus("PAID");
        }
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

    /** Unscoped lookup — callers must authorize first (e.g. the order-stream token). */
    public OrderEntity getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found ID: " + orderId));
    }

    /**
     * Tenant-scoped lookup for staff. Another tenant's order is reported exactly
     * like a missing one (404), so ids cannot be probed across tenants.
     * SUPER_ADMIN may read any order.
     */
    public OrderEntity getOrder(UUID orderId, UserPrincipal principal) {
        OrderEntity order = getOrder(orderId);
        boolean superAdmin = principal != null && principal.getRole() == UserRole.SUPER_ADMIN;
        boolean sameTenant = principal != null && principal.getMerchantId() != null
                && principal.getMerchantId().equals(order.getMerchantId());
        if (!superAdmin && !sameTenant) {
            throw new ResourceNotFoundException("Order not found ID: " + orderId);
        }
        return order;
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

    /**
     * Fetches the merchant's full menu once per order.
     *
     * <p>Replaces a per-line-item {@code GET /api/products/{id}}. That endpoint is
     * not public, and order-service forwards no token when a guest orders, so every
     * anonymous order failed — masked as "Product not found" before, and honestly
     * reported as 503 after the error handling was fixed. {@code GET /api/menu/{id}}
     * is already public, so this needs no credential, and it collapses N calls into
     * one.
     */
    private Map<String, Object> fetchMenu(UUID merchantId) {
        String url = menuServiceUrl + "/api/menu/" + merchantId;
        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                log.error("menu-service returned an empty body for {}", url);
                throw new ServiceUnavailableException("menu-service returned an empty menu payload");
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("No menu found for merchant " + merchantId);
        } catch (HttpStatusCodeException e) {
            log.error("menu-service returned {} for {} — body: {}",
                    e.getStatusCode(), url, e.getResponseBodyAsString());
            throw new ServiceUnavailableException(
                    "menu-service returned " + e.getStatusCode() + " while loading the menu", e);
        } catch (RestClientException e) {
            log.error("menu-service unreachable at {}", url, e);
            throw new ServiceUnavailableException("menu-service is unreachable", e);
        }
    }

    /**
     * Flattens a MenuResponse payload into a product lookup.
     *
     * <p>Pure and package-visible so it can be tested without a running service.
     * Malformed entries are skipped rather than aborting the whole order: one bad
     * row should not stop a guest ordering the other items, and an id that ends up
     * missing is reported precisely by {@link #resolveProduct}.
     */
    static Map<Long, ProductInfo> indexMenu(Map<String, Object> menuBody) {
        Map<Long, ProductInfo> index = new HashMap<>();
        Object categories = menuBody.get("categories");
        if (!(categories instanceof List<?> categoryList)) {
            return index;
        }
        for (Object category : categoryList) {
            if (!(category instanceof Map<?, ?> categoryMap)) {
                continue;
            }
            if (!(categoryMap.get("items") instanceof List<?> items)) {
                continue;
            }
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> productMap)) {
                    continue;
                }
                try {
                    Long id = ((Number) productMap.get("id")).longValue();
                    Integer prep = productMap.get("preparationTime") != null
                            ? ((Number) productMap.get("preparationTime")).intValue()
                            : null;
                    // available defaults to true: a menu payload that omits the flag
                    // must not make the whole menu unorderable.
                    boolean available = !(productMap.get("available") instanceof Boolean b) || b;
                    index.put(id, new ProductInfo(
                            id,
                            (String) productMap.get("name"),
                            new BigDecimal(String.valueOf(productMap.get("price"))),
                            prep,
                            available));
                } catch (NullPointerException | ClassCastException | NumberFormatException e) {
                    log.warn("Skipping malformed menu item {}", productMap, e);
                }
            }
        }
        return index;
    }

    /**
     * Resolves one ordered product, distinguishing the two client errors that were
     * previously indistinguishable — and catching sold-out items, which the old
     * per-item fetch never checked because it ignored the {@code available} flag.
     */
    static ProductInfo resolveProduct(Map<Long, ProductInfo> menu, Long productId) {
        ProductInfo product = menu.get(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found in this merchant's menu: " + productId);
        }
        if (!product.isAvailable()) {
            throw new BusinessException(
                    "\"" + product.getName() + "\" is currently unavailable and cannot be ordered");
        }
        return product;
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
     *
     * @return whether the table status was actually updated, so callers report
     *         the outcome instead of implying success
     */
    private boolean updateTableStatus(Long tableId, String status) {
        String url = merchantServiceUrl + "/api/tables/" + tableId + "/status";
        HttpEntity<Map<String, String>> requestEntity =
                new HttpEntity<>(Map.of("status", status), getAuthHeaders());

        long backoffMs = 100L;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, Map.class);
                return true;
            } catch (HttpClientErrorException e) {
                // 4xx will not become 3xx on retry — stop immediately.
                log.error("Table {} status not set to {}: merchant-service rejected the call with {} ({})",
                        tableId, status, e.getStatusCode(), url);
                return false;
            } catch (RestClientException e) {
                if (attempt == 3) {
                    log.error("Table {} status not set to {} after {} attempts; occupancy is now stale",
                            tableId, status, attempt, e);
                    return false;
                }
                log.warn("Attempt {}/3 to set table {} status to {} failed: {}",
                        attempt, tableId, status, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs *= 2;
            }
        }
        return false;
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

    static class ProductInfo {
        private final Long id;
        private final String name;
        private final BigDecimal price;
        private final Integer preparationTime;
        private final boolean available;

        ProductInfo(Long id, String name, BigDecimal price, Integer preparationTime, boolean available) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.preparationTime = preparationTime;
            this.available = available;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        public Integer getPreparationTime() { return preparationTime; }
        public boolean isAvailable() { return available; }
    }
}