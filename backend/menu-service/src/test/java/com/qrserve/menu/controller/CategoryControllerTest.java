package com.qrserve.menu.controller;

import com.qrserve.menu.dto.UpdateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GET /api/categories} previously took a caller-supplied merchantId with
 * no tenant check at all, so any authenticated role (including CUSTOMER) could
 * list any merchant's categories. These tests guard the fix: non-SUPER_ADMIN
 * callers are pinned to their own tenant regardless of what they ask for, and
 * SUPER_ADMIN may still read across tenants.
 */
class CategoryControllerTest {

    private static final Long CATEGORY_ID = 3L;
    private static final UUID OWN_MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private MenuService menuService;
    private CategoryController controller;

    @BeforeEach
    void setUp() {
        menuService = mock(MenuService.class);
        controller = new CategoryController(menuService);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    @Test
    @DisplayName("a non-admin requesting another merchant's categories is silently pinned to their own tenant")
    void nonAdminIsPinnedToOwnTenant() {
        when(menuService.getCategories(OWN_MERCHANT)).thenReturn(List.of(CategoryEntity.builder().id(1L).build()));

        List<CategoryEntity> body = controller
                .getCategories(OTHER_MERCHANT, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER))
                .getBody();

        assertEquals(1, body.size());
        verify(menuService).getCategories(OWN_MERCHANT);
    }

    @Test
    @DisplayName("SUPER_ADMIN may request an arbitrary merchant's categories")
    void superAdminCrossesTenants() {
        when(menuService.getCategories(OTHER_MERCHANT)).thenReturn(List.of(CategoryEntity.builder().id(2L).build()));

        List<CategoryEntity> body = controller
                .getCategories(OTHER_MERCHANT, principal(OWN_MERCHANT, UserRole.SUPER_ADMIN))
                .getBody();

        assertEquals(1, body.size());
        verify(menuService).getCategories(OTHER_MERCHANT);
    }

    @Test
    @DisplayName("a caller with no merchant scope is rejected")
    void callerWithNoMerchantScopeRejected() {
        assertThrows(UnauthorizedException.class, () ->
                controller.getCategories(OWN_MERCHANT, principal(null, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("an unauthenticated call is rejected")
    void unauthenticatedRejected() {
        assertThrows(UnauthorizedException.class, () -> controller.getCategories(OWN_MERCHANT, null));
    }

    // --- mutations: ids are sequential, so each must check ownership ---

    private static CategoryEntity categoryOwnedBy(UUID merchantId) {
        return CategoryEntity.builder().id(CATEGORY_ID).merchantId(merchantId).build();
    }

    @Test
    @DisplayName("updateCategory refuses a caller from a different tenant")
    void updateCategoryCrossTenantRefused() {
        when(menuService.getCategory(CATEGORY_ID)).thenReturn(categoryOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () -> controller.updateCategory(
                CATEGORY_ID, new UpdateCategoryRequest(), principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuService, never()).updateCategory(any(), any());
    }

    @Test
    @DisplayName("updateCategory proceeds for the owning tenant")
    void updateCategoryWithinTenant() {
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        when(menuService.getCategory(CATEGORY_ID)).thenReturn(categoryOwnedBy(OWN_MERCHANT));

        controller.updateCategory(CATEGORY_ID, request, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER));

        verify(menuService).updateCategory(CATEGORY_ID, request);
    }

    @Test
    @DisplayName("deleteCategory refuses a caller from a different tenant")
    void deleteCategoryCrossTenantRefused() {
        when(menuService.getCategory(CATEGORY_ID)).thenReturn(categoryOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () ->
                controller.deleteCategory(CATEGORY_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuService, never()).deleteCategory(any());
    }

    @Test
    @DisplayName("deleteCategory allows SUPER_ADMIN across tenants")
    void deleteCategorySuperAdminCrossesTenants() {
        when(menuService.getCategory(CATEGORY_ID)).thenReturn(categoryOwnedBy(OTHER_MERCHANT));

        controller.deleteCategory(CATEGORY_ID, principal(OWN_MERCHANT, UserRole.SUPER_ADMIN));

        verify(menuService).deleteCategory(CATEGORY_ID);
    }
}
