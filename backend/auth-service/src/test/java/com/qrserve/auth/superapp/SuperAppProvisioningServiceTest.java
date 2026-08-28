package com.qrserve.auth.superapp;

import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SuperAppProvisioningServiceTest {

    private static final String TOKEN = "raw-token";
    private static final String MERCHANT_REF = "MPESA-BIZ-001";
    private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long BRANCH_ID = 42L;

    private SuperAppAuthPort authPort;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private RestTemplate restTemplate;
    private SuperAppProvisioningService service;

    private static SuperAppMerchantClaim claim() {
        return new SuperAppMerchantClaim(MERCHANT_REF, "Sunrise Cafe", "+254700000000", "Nairobi", "123 Moi Ave", "Restaurant");
    }

    @BeforeEach
    void setUp() {
        authPort = mock(SuperAppAuthPort.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        restTemplate = mock(RestTemplate.class);
        service = new SuperAppProvisioningService(authPort, userRepository, passwordEncoder, tokenProvider, restTemplate);
        ReflectionTestUtils.setField(service, "merchantServiceUrl", "http://merchant-service-test");

        when(authPort.exchangeToken(TOKEN)).thenReturn(claim());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(tokenProvider.generateInternalServiceToken(any(UserPrincipal.class))).thenReturn("system-token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
        when(tokenProvider.getAccessExpirationSeconds()).thenReturn(3600L);
    }

    @Test
    @DisplayName("an existing Super App user logs straight in - no provisioning calls at all")
    void existingUserLogsInWithoutProvisioning() {
        UserEntity existing = UserEntity.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT_ID)
                .name("Sunrise Cafe")
                .email("mpesa-biz-001@superapp.qrserve.internal")
                .passwordHash("hashed")
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .superAppMerchantRef(MERCHANT_REF)
                .build();
        when(userRepository.findBySuperAppMerchantRef(MERCHANT_REF)).thenReturn(Optional.of(existing));

        LoginResponse response = service.exchangeAndLogin(TOKEN);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        verifyNoInteractions(restTemplate);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a new Super App merchant is provisioned: merchant, then branch, then table, then a local user")
    void newMerchantIsProvisionedInOrder() {
        when(userRepository.findBySuperAppMerchantRef(MERCHANT_REF)).thenReturn(Optional.empty());

        MerchantProvisionResponse merchantResponse = new MerchantProvisionResponse(MERCHANT_ID, "sunrise-cafe");
        BranchProvisionResponse branchResponse = new BranchProvisionResponse(BRANCH_ID, "main");

        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/merchants"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(MerchantProvisionResponse.class)))
                .thenReturn(ResponseEntity.ok(merchantResponse));
        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/branches"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(BranchProvisionResponse.class)))
                .thenReturn(ResponseEntity.ok(branchResponse));
        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/tables"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity toSave = inv.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        LoginResponse response = service.exchangeAndLogin(TOKEN);

        assertEquals("access-token", response.getAccessToken());

        InOrder order = inOrder(restTemplate);
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/merchants"), eq(HttpMethod.POST), any(HttpEntity.class), eq(MerchantProvisionResponse.class));
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/branches"), eq(HttpMethod.POST), any(HttpEntity.class), eq(BranchProvisionResponse.class));
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/tables"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals(MERCHANT_ID, saved.getMerchantId());
        assertEquals(UserRole.MERCHANT_OWNER, saved.getRole());
        assertEquals(MERCHANT_REF, saved.getSuperAppMerchantRef());
        assertEquals("mpesa-biz-001@superapp.qrserve.internal", saved.getEmail());
        assertNotNull(saved.getPasswordHash());
        assertEquals(true, saved.isEnabled());
    }
}
