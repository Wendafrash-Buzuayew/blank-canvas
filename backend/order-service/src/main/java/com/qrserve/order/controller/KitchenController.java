package com.qrserve.order.controller;

import com.qrserve.order.dto.KitchenOrderResponse;
import com.qrserve.order.service.KitchenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@Tag(name = "Kitchen Operations", description = "Kitchen Display System (KDS) & Order Board APIs")
public class KitchenController {

    private final KitchenService kitchenService;

    @GetMapping("/orders")
    @Operation(summary = "Get active kitchen orders with filters for pending, preparing, ready, branch, and table")
    public ResponseEntity<List<KitchenOrderResponse>> getKitchenOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(kitchenService.getKitchenOrders(status, branchId, tableId, merchantId));
    }
}
