package com.qrserve.order.service;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.repository.OrderItemRepository;
import com.qrserve.order.repository.OrderRepository;
import com.qrserve.shared.common.OrderStatus;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant scoping on order read/update, the QR-signature gate on order placement,
 * and the rule for when a table is freed.
 */
class OrderServiceSecurityTest {

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long BRANCH = 3L;
    private static final long TABLE = 7L;

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderEventPublisher eventPublisher;
    private RestTemplate restTemplate;
    private QrSignatureService qr;
    private OrderService service;
    private UUID orderId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        eventPublisher = mock(OrderEventPublisher.class);
        restTemplate = mock(RestTemplate.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        qr = new QrSignatureService("a-test-qr-secret", "");
        JwtTokenProvider tokens = new JwtTokenProvider(
                "a-test-secret-that-is-long-enough-for-hs256", 3_600_000L, 604_800_000L);
        service = new OrderService(orderRepository, orderItemRepository, eventPublisher,
                restTemplate, tokens, qr, tx);
        orderId = UUID.randomUUID();
    }

    private OrderEntity order(UUID merchantId, OrderStatus status) {
        return OrderEntity.builder()
                .id(orderId).orderNumber("ORD-1").merchantId(merchantId)
                .branchId(BRANCH).tableId(TABLE).status(status.name())
                .totalAmount(new BigDecimal("10.00")).paymentStatus("UNPAID")
                .build();
    }

    private static UserPrincipal staff(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    private static UpdateOrderStatusRequest to(OrderStatus status) {
        UpdateOrderStatusRequest r = new UpdateOrderStatusRequest();
        r.setStatus(status);
        return r;
    }

    private void stored(OrderEntity order) {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- getOrder / updateOrderStatus tenant scoping ---

    @Test
    @DisplayName("another tenant's order reads as 404")
    void getOrderOtherTenantIsNotFound() {
        stored(order(MERCHANT_A, OrderStatus.PENDING));
        assertThrows(ResourceNotFoundException.class,
                () -> service.getOrder(orderId, staff(MERCHANT_B, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("own tenant and SUPER_ADMIN can read the order")
    void getOrderOwnTenantAndSuperAdmin() {
        OrderEntity o = order(MERCHANT_A, OrderStatus.PENDING);
        stored(o);
        assertSame(o, service.getOrder(orderId, staff(MERCHANT_A, UserRole.KITCHEN)));
        assertSame(o, service.getOrder(orderId, staff(null, UserRole.SUPER_ADMIN)));
    }

    @Test
    @DisplayName("another tenant cannot change an order's status, and nothing is published")
    void updateOtherTenantIsNotFound() {
        stored(order(MERCHANT_A, OrderStatus.PENDING));
        assertThrows(ResourceNotFoundException.class, () -> service.updateOrderStatus(
                orderId, to(OrderStatus.CANCELLED), staff(MERCHANT_B, UserRole.MERCHANT_OWNER)));
        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishOrderStatusUpdated(any());
    }

    @Test
    @DisplayName("PAID also marks the payment as PAID")
    void paidSetsPaymentStatus() {
        stored(order(MERCHANT_A, OrderStatus.DELIVERED));
        when(orderRepository.existsByTableIdAndIdNotAndStatusIn(eq(TABLE), eq(orderId), any())).thenReturn(true);
        OrderEntity updated = service.updateOrderStatus(orderId, to(OrderStatus.PAID),
                staff(MERCHANT_A, UserRole.CASHIER));
        assertEquals("PAID", updated.getPaymentStatus());
    }

    // --- table release rule ---

    @Test
    @DisplayName("a table with another open order stays occupied")
    void tableNotFreedWhileAnotherOrderOpen() {
        stored(order(MERCHANT_A, OrderStatus.READY));
        when(orderRepository.existsByTableIdAndIdNotAndStatusIn(eq(TABLE), eq(orderId), any())).thenReturn(true);

        service.updateOrderStatus(orderId, to(OrderStatus.DELIVERED), staff(MERCHANT_A, UserRole.WAITER));

        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("cancelling the last open order frees the table")
    void cancelFreesTableWhenLastOpenOrder() {
        stored(order(MERCHANT_A, OrderStatus.PENDING));
        when(orderRepository.existsByTableIdAndIdNotAndStatusIn(eq(TABLE), eq(orderId), any())).thenReturn(false);

        service.updateOrderStatus(orderId, to(OrderStatus.CANCELLED), staff(MERCHANT_A, UserRole.WAITER));

        verify(restTemplate).exchange(endsWith("/api/tables/" + TABLE + "/status"),
                eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("open statuses are exactly those that still occupy a table")
    void openStatusesExcludeReleasingOnes() {
        assertTrue(OrderService.OPEN_STATUSES.containsAll(List.of("PENDING", "ACCEPTED", "PREPARING", "READY")));
        assertFalse(OrderService.OPEN_STATUSES.contains("CANCELLED"));
        assertFalse(OrderService.OPEN_STATUSES.contains("PAID"));
        assertFalse(OrderService.OPEN_STATUSES.contains("DELIVERED"));
    }

    // --- QR signature on order placement ---

    @SuppressWarnings("unchecked")
    private void tableLookupReturnsTable() {
        Map<String, Object> table = Map.of("id", TABLE, "branchId", BRANCH,
                "merchantId", MERCHANT_A.toString(), "tableNumber", "T-7");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(table));
    }

    private CreateOrderRequest orderRequest(String signature) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(1);
        CreateOrderRequest r = new CreateOrderRequest();
        r.setTableId(TABLE);
        r.setSignature(signature);
        r.setItems(List.of(item));
        return r;
    }

    @Test
    @DisplayName("an order with a forged QR signature is rejected before anything is saved")
    void forgedSignatureRejected() {
        tableLookupReturnsTable();
        assertThrows(AccessDeniedException.class, () -> service.createOrder(orderRequest("forged")));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("a signature for another table is rejected")
    void signatureForOtherTableRejected() {
        tableLookupReturnsTable();
        String otherTable = qr.generateSignature(MERCHANT_A, BRANCH, TABLE + 1);
        assertThrows(AccessDeniedException.class, () -> service.createOrder(orderRequest(otherTable)));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("a valid signature passes the gate")
    void validSignaturePassesGate() {
        // The stubbed menu is empty, so a gate that let it through fails later on
        // the product lookup — proving the signature itself was accepted.
        tableLookupReturnsTable();
        String valid = qr.generateSignature(MERCHANT_A, BRANCH, TABLE);
        assertThrows(ResourceNotFoundException.class, () -> service.createOrder(orderRequest(valid)));
    }
}
