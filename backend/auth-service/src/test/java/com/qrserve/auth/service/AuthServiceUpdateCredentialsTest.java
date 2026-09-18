package com.qrserve.auth.service;

import com.qrserve.auth.dto.UpdateCredentialsRequest;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Guards the fix for the account-takeover gap flagged in security review: a
 * merely-stolen bearer token must not be enough to permanently swap in a new
 * email/password on an already-set-up account, since this codebase's JWTs
 * are stateless (no revocation list) and there is no password-reset flow to
 * recover through. The one exception is the Super App onboarding window,
 * where the stored password is an unguessable UUID minted by
 * SuperAppProvisioningService that the merchant could never supply anyway.
 */
class AuthServiceUpdateCredentialsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(userRepository, passwordEncoder, mock(JwtTokenProvider.class));
    }

    private static UserPrincipal principal() {
        return UserPrincipal.builder().userId(USER_ID).role(UserRole.MERCHANT_OWNER).build();
    }

    private static UserEntity onboardedUser() {
        return UserEntity.builder()
                .id(USER_ID)
                .email("owner@sunrise.com")
                .passwordHash("hashed-real-password")
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .onboardingComplete(true)
                .build();
    }

    private static UserEntity notYetOnboardedUser() {
        return UserEntity.builder()
                .id(USER_ID)
                .email("174379@superapp.qrserve.internal")
                .passwordHash("hashed-random-uuid")
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .superAppMerchantRef("174379")
                .onboardingComplete(false)
                .build();
    }

    @Test
    @DisplayName("an onboarded account is rejected with no current password supplied")
    void onboardedAccountRequiresCurrentPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardedUser()));
        UpdateCredentialsRequest request = new UpdateCredentialsRequest();
        request.setPassword("new-password-123");

        assertThrows(UnauthorizedException.class, () -> service.updateOwnCredentials(principal(), request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an onboarded account is rejected when the current password does not match")
    void onboardedAccountRejectsWrongCurrentPassword() {
        UserEntity existing = onboardedUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", existing.getPasswordHash())).thenReturn(false);

        UpdateCredentialsRequest request = new UpdateCredentialsRequest();
        request.setCurrentPassword("wrong");
        request.setPassword("new-password-123");

        assertThrows(UnauthorizedException.class, () -> service.updateOwnCredentials(principal(), request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an onboarded account may change its password once the current one is proven")
    void onboardedAccountCanChangeCredentialsWithCorrectCurrentPassword() {
        UserEntity existing = onboardedUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correct", existing.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("hashed-new-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCredentialsRequest request = new UpdateCredentialsRequest();
        request.setCurrentPassword("correct");
        request.setPassword("new-password-123");

        service.updateOwnCredentials(principal(), request);

        assertEquals("hashed-new-password", existing.getPasswordHash());
    }

    @Test
    @DisplayName("a not-yet-onboarded Super App account may set credentials with no current password")
    void notYetOnboardedAccountNeedsNoCurrentPassword() {
        UserEntity existing = notYetOnboardedUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-password-123")).thenReturn("hashed-new-password");
        when(userRepository.existsByEmail("owner@sunrise.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCredentialsRequest request = new UpdateCredentialsRequest();
        request.setEmail("owner@sunrise.com");
        request.setPassword("new-password-123");

        service.updateOwnCredentials(principal(), request);

        assertEquals("owner@sunrise.com", existing.getEmail());
        assertEquals("hashed-new-password", existing.getPasswordHash());
    }

    @Test
    @DisplayName("changing to an email already taken by another account is rejected")
    void rejectsDuplicateEmail() {
        UserEntity existing = notYetOnboardedUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@sunrise.com")).thenReturn(true);

        UpdateCredentialsRequest request = new UpdateCredentialsRequest();
        request.setEmail("taken@sunrise.com");

        assertThrows(IllegalArgumentException.class, () -> service.updateOwnCredentials(principal(), request));
        verify(userRepository, never()).save(any());
    }
}
