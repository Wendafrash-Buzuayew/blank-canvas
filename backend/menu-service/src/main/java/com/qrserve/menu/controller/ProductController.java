package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.UpdateProductRequest;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping
    @Operation(summary = "List products filtered by category or merchant")
    public ResponseEntity<List<ProductEntity>> getProducts(@RequestParam(required = false) Long categoryId,
                                                            @RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(menuService.getProducts(categoryId, merchantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductEntity> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getProduct(id));
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