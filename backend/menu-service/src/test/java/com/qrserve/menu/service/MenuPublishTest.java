package com.qrserve.menu.service;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MenuPublishTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private RestTemplate restTemplate;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MENU_ID = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository, restTemplate);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    /** Stubs the merchant-service branch-ownership lookup used by fetchBranchMerchantId. */
    @SuppressWarnings("unchecked")
    private void stubBranchOwner(UUID ownerMerchantId) {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("merchantId", ownerMerchantId.toString())));
    }

    @Test
    void getFullMenuByMenuIdReturnsItsCategoriesAndProducts() {
        CategoryEntity drinks = CategoryEntity.builder().id(1L).menuId(MENU_ID).name("Drinks").build();
        when(categoryRepository.findByMenuIdOrderByDisplayOrderAsc(MENU_ID)).thenReturn(List.of(drinks));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(
                ProductEntity.builder().id(1L).menuId(MENU_ID).categoryId(1L).name("Espresso")
                        .price(BigDecimal.TEN).available(true).preparationTime(3).build()));

        MenuResponse response = service.getFullMenuByMenuId(MENU_ID);

        assertEquals(1, response.getCategories().size());
        assertEquals("Espresso", response.getCategories().get(0).getItems().get(0).getName());
    }

    @Test
    void publishMarksTheMenuPublishedWithATimestamp() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(MERCHANT);

        MenuEntity published = service.publish(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(MenuEntity.Status.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void publishRefusesABranchWithNoMenuYet() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        stubBranchOwner(MERCHANT);

        assertThrows(ResourceNotFoundException.class,
                () -> service.publish(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    void publishRejectsABranchThatBelongsToAnotherMerchant() {
        UUID otherMerchant = UUID.randomUUID();
        stubBranchOwner(otherMerchant);

        UserPrincipal caller = principal(MERCHANT, UserRole.MERCHANT_OWNER);

        assertThrows(AccessDeniedException.class, () -> service.publish(BRANCH, caller));
        verify(menuRepository, never()).save(any());
    }

    @Test
    void publishAllowsSuperAdminRegardlessOfMerchant() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuEntity published = service.publish(BRANCH, principal(UUID.randomUUID(), UserRole.SUPER_ADMIN));

        assertEquals(MenuEntity.Status.PUBLISHED, published.getStatus());
        // SUPER_ADMIN bypasses the ownership check entirely, so no branch lookup happens.
        verifyNoInteractions(restTemplate);
    }
}
