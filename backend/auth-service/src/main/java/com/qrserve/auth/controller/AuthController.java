package com.qrserve.auth.controller;

import com.qrserve.auth.dto.LoginRequest;
import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.dto.RefreshRequest;
import com.qrserve.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.dto.CreateUserRequest;
import com.qrserve.auth.dto.UserInfoResponse;
import com.qrserve.shared.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User Login, Refresh Token & Logout APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using valid refresh token")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user and invalidate token session")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Successfully logged out"));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_OWNER')")
    @Operation(summary = "Admin/Owner creates a new user (Waiter, Kitchen, Branch Manager, etc.)")
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserEntity createdUser = authService.createUser(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "User created successfully",
            "userId", createdUser.getId(),
            "email", createdUser.getEmail(),
            "role", createdUser.getRole()
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<UserInfoResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserInfoResponse response = authService.getUserInfo(principal);
        return ResponseEntity.ok(response);
    }
}
