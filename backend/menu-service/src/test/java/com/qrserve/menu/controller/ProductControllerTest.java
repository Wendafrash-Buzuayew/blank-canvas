package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.UpdateProductRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.ProductEntity;
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
 * {@code GET /api/products} and {@code GET /api/products/{id}} previously had
 * no tenant check at all — a categoryId alone bypassed merchant filtering
 * entirely at the service layer. These tests guard the fix: non-SUPER_ADMIN
 * callers are pinned to their own tenant, and a category/product belonging to
 * a different merchant is refused rather than silently returned.
 */
class ProductControllerTest {

    private static final Long PRODUCT_ID = 7L;
    private static final Long CATEGORY_ID = 3L;
    private static final UUID OWN_MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private MenuService menuService;
    private ProductController controller;

    @BeforeEach
    void setUp() {
        menuService = mock(MenuService.class);
        controller = new ProductController(menuService);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    private static ProductEntity productOwnedBy(UUID merchantId) {
        return ProductEntity.builder().id(PRODUCT_ID).merchantId(merchantId).categoryId(CATEGORY_ID).build();
    }

    // --- GET /api/products ---

    @Test
    @DisplayName("a non-admin requesting another merchant's products is silently pinned to their own tenant")
    void nonAdminIsPinnedToOwnTenant() {
        when(menuService.getProducts(null, OWN_MERCHANT)).thenReturn(List.of(productOwnedBy(OWN_MERCHANT)));

        List<ProductEntity> body = controller
                .getProducts(null, OTHER_MERCHANT, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER))
                .getBody();

        assertEquals(1, body.size());
    }

    @Test
    @DisplayName("a categoryId belonging to another tenant is refused, not silently returned")
    void categoryFromAnotherTenantRefused() {
        // Caller passes a categoryId that resolves to another merchant's products;
        // before the fix, MenuService.getProducts ignored the merchantId filter
        // entirely whenever categoryId was present, so this leaked cross-tenant.
        when(menuService.getProducts(CATEGORY_ID, OWN_MERCHANT)).thenReturn(List.of(productOwnedBy(OTHER_MERCHANT)));

        assertThrows(UnauthorizedException.class, () ->
                controller.getProducts(CATEGORY_ID, null, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("SUPER_ADMIN may request an arbitrary merchant's products")
    void superAdminCrossesTenants() {
        when(menuService.getProducts(null, OTHER_MERCHANT)).thenReturn(List.of(productOwnedBy(OTHER_MERCHANT)));

        List<ProductEntity> body = controller
                .getProducts(null, OTHER_MERCHANT, principal(OWN_MERCHANT, UserRole.SUPER_ADMIN))
                .getBody();

        assertEquals(1, body.size());
    }

    // --- GET /api/products/{id} ---

    @Test
    @DisplayName("getProduct returns the product when the caller owns the tenant")
    void getProductWithinTenant() {
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OWN_MERCHANT));

        ProductEntity body = controller
                .getProduct(PRODUCT_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER))
                .getBody();

        assertEquals(PRODUCT_ID, body.getId());
    }

    @Test
    @DisplayName("getProduct refuses a caller from a different tenant")
    void getProductCrossTenantRefused() {
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () ->
                controller.getProduct(PRODUCT_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("getProduct allows SUPER_ADMIN to read across tenants")
    void getProductSuperAdminCrossesTenants() {
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OTHER_MERCHANT));

        ProductEntity body = controller
                .getProduct(PRODUCT_ID, principal(OWN_MERCHANT, UserRole.SUPER_ADMIN))
                .getBody();

        assertEquals(PRODUCT_ID, body.getId());
    }

    // --- mutations: ids are sequential, so each must check ownership ---

    @Test
    @DisplayName("createProduct refuses a categoryId belonging to another tenant")
    void createProductInForeignCategoryRefused() {
        when(menuService.getCategory(CATEGORY_ID))
                .thenReturn(CategoryEntity.builder().id(CATEGORY_ID).merchantId(OTHER_MERCHANT).build());
        CreateProductRequest request = CreateProductRequest.builder().categoryId(CATEGORY_ID).name("x").build();

        assertThrows(UnauthorizedException.class, () ->
                controller.createProduct(request, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuService, never()).createProduct(any());
    }

    @Test
    @DisplayName("updateProduct refuses a caller from a different tenant")
    void updateProductCrossTenantRefused() {
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () -> controller.updateProduct(
                PRODUCT_ID, new UpdateProductRequest(), principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuService, never()).updateProduct(any(), any());
    }

    @Test
    @DisplayName("updateProduct proceeds for the owning tenant")
    void updateProductWithinTenant() {
        UpdateProductRequest request = new UpdateProductRequest();
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OWN_MERCHANT));
        when(menuService.updateProduct(PRODUCT_ID, request)).thenReturn(productOwnedBy(OWN_MERCHANT));

        controller.updateProduct(PRODUCT_ID, request, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER));

        verify(menuService).updateProduct(PRODUCT_ID, request);
    }

    @Test
    @DisplayName("deleteProduct refuses a caller from a different tenant")
    void deleteProductCrossTenantRefused() {
        when(menuService.getProduct(PRODUCT_ID)).thenReturn(productOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () ->
                controller.deleteProduct(PRODUCT_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
        verify(menuService, never()).deleteProduct(any());
    }
}
