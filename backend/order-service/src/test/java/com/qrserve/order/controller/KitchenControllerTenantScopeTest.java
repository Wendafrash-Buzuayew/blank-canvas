package com.qrserve.order.controller;

import com.qrserve.order.service.KitchenService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The kitchen board must never return another tenant's orders. */
class KitchenControllerTenantScopeTest {

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private KitchenService kitchenService;
    private KitchenController controller;

    @BeforeEach
    void setUp() {
        kitchenService = mock(KitchenService.class);
        controller = new KitchenController(kitchenService);
    }

    private static UserPrincipal staff(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().merchantId(merchantId).role(role).build();
    }

    @Test
    @DisplayName("staff are pinned to their own tenant whatever merchantId they pass")
    void staffPinnedToOwnTenant() {
        controller.getKitchenOrders("PENDING", 5L, 9L, MERCHANT_B, staff(MERCHANT_A, UserRole.KITCHEN));
        verify(kitchenService).getKitchenOrders("PENDING", 5L, 9L, MERCHANT_A);
    }

    @Test
    @DisplayName("staff with no filters still get only their tenant, never findAll")
    void staffWithoutFiltersStayScoped() {
        controller.getKitchenOrders(null, null, null, null, staff(MERCHANT_A, UserRole.BRANCH_MANAGER));
        verify(kitchenService).getKitchenOrders(null, null, null, MERCHANT_A);
    }

    @Test
    @DisplayName("SUPER_ADMIN may choose the tenant")
    void superAdminChoosesTenant() {
        controller.getKitchenOrders(null, null, null, MERCHANT_B, staff(null, UserRole.SUPER_ADMIN));
        verify(kitchenService).getKitchenOrders(null, null, null, MERCHANT_B);
    }

    @Test
    @DisplayName("a non-admin token without a merchant is refused")
    void noMerchantScopeRefused() {
        assertThrows(UnauthorizedException.class,
                () -> controller.getKitchenOrders(null, null, null, MERCHANT_B, staff(null, UserRole.KITCHEN)));
        verify(kitchenService, never()).getKitchenOrders(any(), any(), any(), any());
    }
}
