package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.UpdateProductRequest;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Menu Products", description = "Menu Item & Product Management APIs")
public class ProductController {

    private final MenuService menuService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Create product item")
    public ResponseEntity<ProductEntity> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(menuService.createProduct(request));
    }

    /**
     * Was reachable by any authenticated role (including CUSTOMER) with no tenant
     * check, so any logged-in user could list any merchant's products by
     * supplying an arbitrary merchantId — or bypass that entirely by supplying
     * only a categoryId, which the service resolved with no merchant filter at
     * all. Non-SUPER_ADMIN callers are pinned to their own tenant, and the result
     * is verified to actually belong to that tenant before it's returned (guards
     * the categoryId-only path, where a category from another merchant would
     * otherwise slip through unfiltered).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "List products filtered by category or merchant")
    public ResponseEntity<List<ProductEntity>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID scope = resolveScope(merchantId, principal);
        List<ProductEntity> products = menuService.getProducts(categoryId, scope);
        if (scope != null && products.stream().anyMatch(p -> !scope.equals(p.getMerchantId()))) {
            throw new UnauthorizedException("Caller has no access to category " + categoryId);
        }
        return ResponseEntity.ok(products);
    }

    /**
     * Same tenant-leak class as {@link #getProducts}: a caller could walk product
     * ids across merchants with no ownership check.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductEntity> getProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProductEntity product = menuService.getProduct(id);
        requireTenantAccess(product.getMerchantId(), principal);
        return ResponseEntity.ok(product);
    }

    private UUID resolveScope(UUID requested, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return requested;
        }
        UUID own = principal.getMerchantId();
        if (own == null) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return own;
    }

    private void requireTenantAccess(UUID merchantId, UserPrincipal principal) {
        boolean superAdmin = principal != null && principal.getRole() == UserRole.SUPER_ADMIN;
        boolean sameTenant = principal != null && merchantId.equals(principal.getMerchantId());
        if (!superAdmin && !sameTenant) {
            throw new UnauthorizedException("Caller has no access to merchant " + merchantId);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Update product item")
    public ResponseEntity<ProductEntity> updateProduct(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(menuService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Delete product item")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        menuService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}