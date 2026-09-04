package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.dto.UpdateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Menu Categories", description = "Menu Category Creation & Reordering APIs")
public class CategoryController {

    private final MenuService menuService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Create menu category")
    public ResponseEntity<CategoryEntity> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID merchantId = resolveScope(request.getMerchantId(), principal);
        return ResponseEntity.ok(menuService.createCategory(request, merchantId));
    }

    /**
     * Was reachable by any authenticated role (including CUSTOMER) with no tenant
     * check, so any logged-in user could list any merchant's categories by
     * supplying an arbitrary merchantId. Mirrors the scoping rule used by
     * AnalyticsController.resolveScope: only SUPER_ADMIN may pass an arbitrary
     * merchantId, everyone else is pinned to the tenant in their own token.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "List categories for a merchant")
    public ResponseEntity<List<CategoryEntity>> getCategories(
            @RequestParam UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(menuService.getCategories(resolveScope(merchantId, principal)));
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Update menu category")
    public ResponseEntity<CategoryEntity> updateCategory(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(menuService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Delete menu category (cascades products)")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}