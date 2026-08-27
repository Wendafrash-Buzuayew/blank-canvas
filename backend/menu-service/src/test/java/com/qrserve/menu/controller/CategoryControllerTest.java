package com.qrserve.menu.controller;

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
import static org.mockito.Mockito.mock;
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
}
