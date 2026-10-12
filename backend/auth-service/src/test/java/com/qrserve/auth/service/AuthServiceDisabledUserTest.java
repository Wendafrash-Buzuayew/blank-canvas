package com.qrserve.auth.service;

import com.qrserve.auth.dto.LoginRequest;
import com.qrserve.auth.dto.RefreshRequest;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A disabled account must not obtain new tokens by password login or by refresh. */
class AuthServiceDisabledUserTest {

    private static final String EMAIL = "waiter@sunrise.com";
    private static final String REFRESH = "refresh-token";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        service = new AuthService(userRepository, passwordEncoder, tokenProvider);

        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(tokenProvider.validateToken(REFRESH)).thenReturn(true);
        when(tokenProvider.isRefreshToken(REFRESH)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(REFRESH)).thenReturn(EMAIL);
        when(tokenProvider.generateAccessToken(any())).thenReturn("access");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh");
    }

    private void givenUser(boolean enabled) {
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash("hash")
                .role(UserRole.WAITER)
                .enabled(enabled)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private static LoginRequest login() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("secret");
        return request;
    }

    private static RefreshRequest refresh() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(REFRESH);
        return request;
    }

    @Test
    @DisplayName("login is refused for a disabled user even with the right password")
    void loginRejectsDisabledUser() {
        givenUser(false);
        assertThrows(UnauthorizedException.class, () -> service.login(login()));
        verify(tokenProvider, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh is refused for a disabled user")
    void refreshRejectsDisabledUser() {
        givenUser(false);
        assertThrows(UnauthorizedException.class, () -> service.refreshToken(refresh()));
        verify(tokenProvider, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("enabled users still log in and refresh")
    void enabledUserStillWorks() {
        givenUser(true);
        assertNotNull(service.login(login()).getAccessToken());
        assertNotNull(service.refreshToken(refresh()).getAccessToken());
    }
}
