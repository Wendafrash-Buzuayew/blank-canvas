package com.qrserve.menu.controller;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
