package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Table Management & QR URL Provisioning APIs")
public class TableController {

    private final TableService tableService;

    @PostMapping
    @Operation(summary = "Create a table and auto-generate QR menu URL")
    public ResponseEntity<CreateTableResponse> createTable(@Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.ok(tableService.createTable(request));
    }

    @GetMapping("/all")
    @Operation(summary = "List all tables (inter-service for analytics)")
    public ResponseEntity<List<TableEntity>> getAllTables() {
        return ResponseEntity.ok(tableService.getAllTables());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get table details by ID")
    public ResponseEntity<TableEntity> getTable(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.getTable(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update table status (inter-service)")
    public ResponseEntity<TableEntity> updateTableStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "AVAILABLE");
        return ResponseEntity.ok(tableService.updateTableStatus(id, status));
    }
}