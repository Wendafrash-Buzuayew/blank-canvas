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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .expiresIn(3600)
                .build();
    }

    public LoginResponse refreshToken(RefreshRequest request) {
        if (!tokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
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
                .expiresIn(3600)
                .build();
    }

    @Transactional
    public UserEntity createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with email " + request.getEmail() + " already exists.");
        }

        UserEntity user = UserEntity.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .merchantId(request.getMerchantId())
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
}
