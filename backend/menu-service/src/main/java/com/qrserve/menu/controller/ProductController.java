package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Menu Products", description = "Menu Item & Product Management APIs")
public class ProductController {

    private final MenuService menuService;
    private final ProductRepository productRepository;

    @PostMapping
    @Operation(summary = "Create product item")
    public ResponseEntity<ProductEntity> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(menuService.createProduct(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductEntity> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found ID: " + id)));
    }
}