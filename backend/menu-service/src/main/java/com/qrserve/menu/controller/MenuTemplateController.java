package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateMenuTemplateRequest;
import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.service.MenuTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET is public (SecurityConfig's existing "/api/menu/*" permitAll rule
 * already covers this single-segment path, method-scoped to GET only) - the
 * customer-facing digital menu needs the active template's definition to
 * render, unauthenticated. POST/PUT/DELETE fall through to
 * anyRequest().authenticated() and are additionally SUPER_ADMIN-only here.
 */
@RestController
@RequestMapping("/api/menu/templates")
@RequiredArgsConstructor
@Tag(name = "Menu Templates", description = "The visual definitions behind the curated digital-menu templates")
public class MenuTemplateController {

    private final MenuTemplateService menuTemplateService;

    @GetMapping
    @Operation(summary = "List every template definition (public - the customer digital menu reads this)")
    public ResponseEntity<List<MenuTemplateEntity>> getAll() {
        return ResponseEntity.ok(menuTemplateService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new template definition")
    public ResponseEntity<MenuTemplateEntity> create(@Valid @RequestBody CreateMenuTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuTemplateService.create(request));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update a template definition's display name, background mode, or accent")
    public ResponseEntity<MenuTemplateEntity> update(
            @PathVariable String key, @Valid @RequestBody UpdateMenuTemplateRequest request) {
        return ResponseEntity.ok(menuTemplateService.update(key, request));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete a template definition (refused if any branch still uses it, or if it is the last one)")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        menuTemplateService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
