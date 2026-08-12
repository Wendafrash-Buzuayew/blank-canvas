package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateWaiterRequest;
import com.qrserve.merchant.dto.UpdateWaiterRequest;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.service.WaiterService;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserRole;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.nio.file.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;



import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/waiters")
@RequiredArgsConstructor
@Tag(name = "Waiters", description = "Waiter Management & Shift Assignment APIs")
public class WaiterController {

    private final WaiterService waiterService;
    private final JwtTokenProvider jwtTokenProvider;
    @PostMapping
    @Operation(summary = "Create a new waiter for a merchant branch")
    public ResponseEntity<WaiterEntity> createWaiter(@Valid @RequestBody CreateWaiterRequest request) {
        WaiterEntity waiter = WaiterEntity.builder()
                .merchantId(request.getMerchantId())
                .branchId(request.getBranchId())
                .userId(request.getUserId())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(waiterService.createWaiter(waiter));
    }

    @GetMapping
    @Operation(summary = "List waiters for the authenticated user")
    public ResponseEntity<List<WaiterEntity>> getWaiters(
             @RequestParam(required = false) Long branchId,
        HttpServletRequest request) {

        String token = extractToken(request);

        UserRole role = jwtTokenProvider.getRoleFromToken(token);

        if (role == UserRole.SUPER_ADMIN) {

            if (branchId != null) {
                return ResponseEntity.ok(
                        waiterService.getWaitersByBranch(branchId)
                );
            }

            return ResponseEntity.ok(
                    waiterService.getAllWaiters()
            );
        }

        if (role == UserRole.MERCHANT_OWNER) {

            UUID merchantId = jwtTokenProvider.getMerchantIdFromToken(token);

            if (merchantId == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Merchant context is missing"
                );
            }

            if (branchId != null) {
                return ResponseEntity.ok(
                        waiterService.getWaitersByMerchantAndBranch(
                                merchantId,
                                branchId
                        )
                );
            }

            return ResponseEntity.ok(
                    waiterService.getWaitersByMerchant(merchantId)
            );
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not allowed to view waiters"
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get waiter by ID")
    public ResponseEntity<WaiterEntity> getWaiter(@PathVariable Long id, @RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(waiterService.getWaiter(id, merchantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update waiter status / shift")
    public ResponseEntity<WaiterEntity> updateWaiter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWaiterRequest request,
            @RequestParam(required = false) UUID merchantId) {
        WaiterEntity updates = WaiterEntity.builder()
                .status(request.getStatus())
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(waiterService.updateWaiter(id, updates, merchantId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a waiter (soft delete)")
    public ResponseEntity<Void> deleteWaiter(@PathVariable Long id, @RequestParam(required = false) UUID merchantId) {
        waiterService.deleteWaiter(id, merchantId);
        return ResponseEntity.noContent().build();
    }

    private String extractToken(HttpServletRequest request) {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid authorization token"
            );
        }

        return authorizationHeader.substring(7);
    }
}