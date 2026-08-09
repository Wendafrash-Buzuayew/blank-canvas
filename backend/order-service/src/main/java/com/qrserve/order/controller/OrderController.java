package com.qrserve.order.controller;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.CreateOrderResponse;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "QR Menu Ordering & Status Tracking APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new customer table order")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping
    @Operation(summary = "List all orders (inter-service for analytics)")
    public ResponseEntity<List<OrderEntity>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderEntity> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order lifecycle status (ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED)")
    public ResponseEntity<OrderEntity> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }
}