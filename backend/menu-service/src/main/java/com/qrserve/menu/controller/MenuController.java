package com.qrserve.menu.controller;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.dto.SetMenuTemplateRequest;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@Tag(name = "Customer Menu", description = "Public & Merchant Menu Hierarchy APIs")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get full published digital menu with categories & products")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(menuService.getFullMenu(merchantId));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Get the published digital menu for a branch (public, phase-1 URL family)")
    public ResponseEntity<MenuResponse> getMenuForBranch(@PathVariable Long branchId) {
        MenuEntity menu = menuService.getMenuForBranch(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("No menu for branch " + branchId));
        if (menu.getStatus() != MenuEntity.Status.PUBLISHED) {
            throw new ResourceNotFoundException("No published menu for branch " + branchId);
        }
        MenuResponse response = menuService.getFullMenuByMenuId(menu.getId());
        response.setTemplateStyle(menu.getTemplateStyle());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/branch/{branchId}/manage")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Get a branch's menu for editing, draft or published, staff-only")
    public ResponseEntity<MenuResponse> getMenuForBranchManagement(
            @PathVariable Long branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(menuService.getMenuForBranchManagement(branchId, principal));
    }

    @PatchMapping("/branch/{branchId}/template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Set a branch's curated digital-menu visual template")
    public ResponseEntity<MenuEntity> setTemplate(
            @PathVariable Long branchId, @Valid @RequestBody SetMenuTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(menuService.setTemplate(branchId, request.getTemplateStyle(), principal));
    }

    @PostMapping("/branch/{branchId}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Publish a branch's menu, making it publicly reachable")
    public ResponseEntity<MenuEntity> publish(
            @PathVariable Long branchId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(menuService.publish(branchId, principal));
    }
}
