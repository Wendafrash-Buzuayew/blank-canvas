package com.qrserve.menu.service;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.menu.storage.MediaStorageService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * getMenuForBranchManagement is the menu-builder UI's draft-inclusive,
 * staff-only read (backs GET /api/menu/branch/{branchId}/manage). Same
 * tenant-ownership check as createCategory/publish, so the same
 * verification shape applies here.
 */
class MenuServiceBranchManagementTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private RestTemplate restTemplate;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository, restTemplate,
                mock(PlatformTransactionManager.class), mock(MediaStorageService.class), mock(MenuTemplateRepository.class));
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
    void returnsTheBranchsFullMenuRegardlessOfPublishStatus() {
        UUID menuId = UUID.randomUUID();
        MenuEntity menu = MenuEntity.builder().id(menuId).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(categoryRepository.findByMenuIdOrderByDisplayOrderAsc(menuId)).thenReturn(List.of(
                CategoryEntity.builder().id(1L).menuId(menuId).merchantId(MERCHANT).name("Drinks").build()));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of());
        stubBranchOwner(MERCHANT);

        MenuResponse response = service.getMenuForBranchManagement(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(1, response.getCategories().size());
        assertEquals("Drinks", response.getCategories().get(0).getName());
    }

    @Test
    void returnsAnEmptyMenuWhenTheBranchHasNoneYet() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        stubBranchOwner(MERCHANT);

        MenuResponse response = service.getMenuForBranchManagement(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertTrue(response.getCategories().isEmpty());
    }

    @Test
    void rejectsABranchThatBelongsToAnotherMerchant() {
        UUID otherMerchant = UUID.randomUUID();
        stubBranchOwner(otherMerchant);

        assertThrows(AccessDeniedException.class,
                () -> service.getMenuForBranchManagement(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    void superAdminSucceedsRegardlessOfMerchant() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());

        MenuResponse response = service.getMenuForBranchManagement(BRANCH, principal(UUID.randomUUID(), UserRole.SUPER_ADMIN));

        assertNotNull(response);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void nullPrincipalIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> service.getMenuForBranchManagement(BRANCH, null));
    }
}
