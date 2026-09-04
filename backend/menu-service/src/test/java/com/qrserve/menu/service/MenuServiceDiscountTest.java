package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.UpdateProductRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Promotional pricing: MenuService.effectivePrice (the discount-window
 * decision) and the validation createProduct/updateProduct apply to it.
 */
class MenuServiceDiscountTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuService service;
    private static final Long CATEGORY_ID = 3L;
    private static final Long PRODUCT_ID = 7L;
    private static final UUID MENU_ID = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new MenuService(categoryRepository, productRepository, mock(MenuRepository.class),
                mock(RestTemplate.class), mock(PlatformTransactionManager.class));
    }

    private static ProductEntity product(BigDecimal price, BigDecimal discountPrice,
                                          LocalDateTime start, LocalDateTime end) {
        return ProductEntity.builder().price(price).discountPrice(discountPrice)
                .discountStartAt(start).discountEndAt(end).build();
    }

    // ============ effectivePrice ============

    @Test
    void noDiscountPriceMeansTheRegularPriceApplies() {
        ProductEntity p = product(BigDecimal.TEN, null, null, null);
        assertEquals(BigDecimal.TEN, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void unboundedDiscountIsAlwaysActiveOnceSet() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, null, null);
        assertEquals(BigDecimal.ONE, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void beforeTheStartTheRegularPriceApplies() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, NOW.plusHours(1), null);
        assertEquals(BigDecimal.TEN, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void atTheExactStartTheDiscountApplies() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, NOW, null);
        assertEquals(BigDecimal.ONE, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void afterTheEndTheRegularPriceApplies() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, null, NOW.minusMinutes(1));
        assertEquals(BigDecimal.TEN, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void atTheExactEndTheDiscountStillApplies() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, null, NOW);
        assertEquals(BigDecimal.ONE, MenuService.effectivePrice(p, NOW));
    }

    @Test
    void withinTheWindowTheDiscountApplies() {
        ProductEntity p = product(BigDecimal.TEN, BigDecimal.ONE, NOW.minusHours(1), NOW.plusHours(1));
        assertEquals(BigDecimal.ONE, MenuService.effectivePrice(p, NOW));
    }

    // ============ createProduct validation ============

    @Test
    void createProductRejectsADiscountPriceThatIsNotBelowThePrice() {
        CategoryEntity category = CategoryEntity.builder().id(CATEGORY_ID).menuId(MENU_ID).merchantId(MERCHANT).build();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(CATEGORY_ID).name("Espresso").price(BigDecimal.TEN).discountPrice(BigDecimal.TEN).build();

        assertThrows(BusinessException.class, () -> service.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProductRejectsAnEndBeforeItsOwnStart() {
        CategoryEntity category = CategoryEntity.builder().id(CATEGORY_ID).menuId(MENU_ID).merchantId(MERCHANT).build();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(CATEGORY_ID).name("Espresso").price(BigDecimal.TEN).discountPrice(BigDecimal.ONE)
                .discountStartAt(NOW).discountEndAt(NOW.minusHours(1)).build();

        assertThrows(BusinessException.class, () -> service.createProduct(request));
    }

    @Test
    void createProductAcceptsAValidDiscount() {
        CategoryEntity category = CategoryEntity.builder().id(CATEGORY_ID).menuId(MENU_ID).merchantId(MERCHANT).build();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductEntity product = service.createProduct(CreateProductRequest.builder()
                .categoryId(CATEGORY_ID).name("Espresso").price(BigDecimal.TEN).discountPrice(BigDecimal.ONE)
                .discountStartAt(NOW).discountEndAt(NOW.plusHours(1)).build());

        assertEquals(BigDecimal.ONE, product.getDiscountPrice());
    }

    // ============ updateProduct ============

    @Test
    void updateProductRejectsADiscountPriceThatIsNotBelowThePrice() {
        ProductEntity existing = ProductEntity.builder().id(PRODUCT_ID).price(BigDecimal.TEN).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setDiscountPrice(BigDecimal.valueOf(15));

        assertThrows(BusinessException.class, () -> service.updateProduct(PRODUCT_ID, request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProductClearDiscountRemovesAnExistingDiscountWithoutValidating() {
        ProductEntity existing = ProductEntity.builder().id(PRODUCT_ID).price(BigDecimal.TEN)
                .discountPrice(BigDecimal.ONE).discountStartAt(NOW).discountEndAt(NOW.plusHours(1)).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setClearDiscount(true);

        ProductEntity result = service.updateProduct(PRODUCT_ID, request);

        assertNull(result.getDiscountPrice());
        assertNull(result.getDiscountStartAt());
        assertNull(result.getDiscountEndAt());
    }

    @Test
    void updateProductCanLowerThePriceBelowAnExistingDiscount() {
        // Existing discount (5) is still valid against the new price (6) — must not
        // spuriously reject just because the request itself carries no discount fields.
        ProductEntity existing = ProductEntity.builder().id(PRODUCT_ID).price(BigDecimal.TEN)
                .discountPrice(BigDecimal.valueOf(5)).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(6));

        ProductEntity result = service.updateProduct(PRODUCT_ID, request);

        assertEquals(BigDecimal.valueOf(6), result.getPrice());
        assertEquals(BigDecimal.valueOf(5), result.getDiscountPrice());
    }

    @Test
    void updateProductRejectsLoweringThePriceBelowAnExistingDiscount() {
        ProductEntity existing = ProductEntity.builder().id(PRODUCT_ID).price(BigDecimal.TEN)
                .discountPrice(BigDecimal.valueOf(5)).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(4));

        assertThrows(BusinessException.class, () -> service.updateProduct(PRODUCT_ID, request));
    }
}
