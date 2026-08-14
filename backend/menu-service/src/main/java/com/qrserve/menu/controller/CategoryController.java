package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.dto.UpdateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "Create menu category")
    public ResponseEntity<CategoryEntity> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(menuService.createCategory(request));
    }

    @GetMapping
    @Operation(summary = "List categories for a merchant")
    public ResponseEntity<List<CategoryEntity>> getCategories(@RequestParam UUID merchantId) {
        return ResponseEntity.ok(menuService.getCategories(merchantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update menu category")
    public ResponseEntity<CategoryEntity> updateCategory(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(menuService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete menu category (cascades products)")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}