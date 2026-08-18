package com.qrserve.auth.service;

import com.qrserve.auth.dto.CreateUserRequest;
import com.qrserve.auth.dto.LoginRequest;
import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.dto.RefreshRequest;
import com.qrserve.auth.dto.UserInfoResponse;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!matches) {
            throw new UnauthorizedException("Invalid email or password");
        }

        UserPrincipal principal = UserPrincipal.builder()
                .userId(user.getId())
                .merchantId(user.getMerchantId())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .build();

        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .build();
    }

    public LoginResponse refreshToken(RefreshRequest request) {
        if (!tokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        // An access token was previously accepted here as a refresh token, so a
        // stolen access token could be traded for a fresh pair indefinitely.
        if (!tokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }

        String email = tokenProvider.getUsernameFromToken(request.getRefreshToken());
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserPrincipal principal = UserPrincipal.builder()
                .userId(user.getId())
                .merchantId(user.getMerchantId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        String newAccessToken = tokenProvider.generateAccessToken(principal);
        String newRefreshToken = tokenProvider.generateRefreshToken(principal);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .build();
    }

    /** Lower rank = more privilege. */
    private static int rankOf(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> 0;
            case MERCHANT_OWNER -> 1;
            case BRANCH_MANAGER -> 2;
            case WAITER, KITCHEN, CASHIER -> 3;
            case CUSTOMER -> 4;
        };
    }

    /**
     * Whether {@code caller} may create a user holding {@code target}.
     *
     * <p>A caller may only create roles strictly less privileged than their own —
     * not even a peer. Without this, {@code POST /api/auth/users} let any
     * MERCHANT_OWNER mint a SUPER_ADMIN, i.e. full privilege escalation from the
     * lowest role that can reach the endpoint.
     *
     * <p>Package-private so it can be unit-tested without a Spring context.
     */
    static boolean canAssignRole(UserRole caller, UserRole target) {
        if (caller == null || target == null) {
            return false;
        }
        if (caller == UserRole.SUPER_ADMIN) {
            return true;
        }
        return rankOf(target) > rankOf(caller);
    }

    @Transactional
    public UserEntity createUser(CreateUserRequest request, UserPrincipal caller) {
        if (caller == null || caller.getRole() == null) {
            throw new UnauthorizedException("Authentication is required to create a user");
        }
        if (!canAssignRole(caller.getRole(), request.getRole())) {
            throw new UnauthorizedException("Role " + caller.getRole()
                    + " may not create a user with role " + request.getRole());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with email " + request.getEmail() + " already exists.");
        }

        // Non-super-admins may only create users inside their own tenant; the
        // caller-supplied merchantId is ignored for them.
        UUID merchantId;
        if (caller.getRole() == UserRole.SUPER_ADMIN) {
            merchantId = request.getMerchantId();
        } else {
            merchantId = caller.getMerchantId();
            if (merchantId == null) {
                throw new UnauthorizedException("Caller has no merchant scope; cannot create users");
            }
        }

        UserEntity user = UserEntity.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .merchantId(merchantId)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    public UserInfoResponse getUserInfo(UserPrincipal principal) {
        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + principal.getUserId()));

        return UserInfoResponse.builder()
                .id(user.getId())
                .merchantId(user.getMerchantId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }

    /**
     * List users, optionally scoped to a merchant. Used by admin/owner consoles to
     * resolve human-readable user names instead of exposing UUIDs in the UI.
     */
    @Transactional(readOnly = true)
    public List<UserInfoResponse> listUsers(UUID merchantId) {
        List<UserEntity> users = merchantId != null
                ? userRepository.findByMerchantId(merchantId)
                : userRepository.findAll();

        return users.stream()
                .sorted(Comparator.comparing(UserEntity::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(user -> UserInfoResponse.builder()
                        .id(user.getId())
                        .merchantId(user.getMerchantId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .enabled(user.isEnabled())
                        .build())
                .collect(Collectors.toList());
    }
}
