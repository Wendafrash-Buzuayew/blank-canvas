package com.qrserve.menu.service;

import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** setTemplate: same tenant-ownership shape as publish/createCategory, plus auto-creating the menu. */
class MenuServiceTemplateTest {

    private MenuRepository menuRepository;
    private RestTemplate restTemplate;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        menuRepository = mock(MenuRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new MenuService(mock(CategoryRepository.class), mock(ProductRepository.class), menuRepository,
                restTemplate, mock(PlatformTransactionManager.class));
    }

    @SuppressWarnings("unchecked")
    private void stubBranchOwner(UUID ownerMerchantId) {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("merchantId", ownerMerchantId.toString())));
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    @Test
    void setsTheTemplateOnAnExistingMenu() {
        MenuEntity menu = MenuEntity.builder().id(UUID.randomUUID()).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).templateStyle(MenuEntity.TemplateStyle.CLASSIC).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(MERCHANT);

        MenuEntity result = service.setTemplate(BRANCH, MenuEntity.TemplateStyle.MODERN_DARK, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(MenuEntity.TemplateStyle.MODERN_DARK, result.getTemplateStyle());
    }

    @Test
    void createsAMenuWhenNoneExistsYet() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(MERCHANT);

        MenuEntity result = service.setTemplate(BRANCH, MenuEntity.TemplateStyle.VIBRANT, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(MERCHANT, result.getMerchantId());
        assertEquals(MenuEntity.TemplateStyle.VIBRANT, result.getTemplateStyle());
    }

    @Test
    void rejectsABranchThatBelongsToAnotherMerchant() {
        stubBranchOwner(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () ->
                service.setTemplate(BRANCH, MenuEntity.TemplateStyle.VIBRANT, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuRepository, never()).save(any());
    }

    @Test
    void superAdminSucceedsRegardlessOfMerchant() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(MERCHANT);

        MenuEntity result = service.setTemplate(BRANCH, MenuEntity.TemplateStyle.CLASSIC, principal(UUID.randomUUID(), UserRole.SUPER_ADMIN));

        assertEquals(MenuEntity.TemplateStyle.CLASSIC, result.getTemplateStyle());
    }

    @Test
    void nullPrincipalIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> service.setTemplate(BRANCH, MenuEntity.TemplateStyle.CLASSIC, null));
    }
}
