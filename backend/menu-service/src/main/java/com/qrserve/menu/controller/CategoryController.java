package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
