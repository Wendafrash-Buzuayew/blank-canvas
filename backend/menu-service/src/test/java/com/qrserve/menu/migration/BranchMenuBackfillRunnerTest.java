package com.qrserve.menu.migration;

import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The runner's actual REST calls to merchant-service aren't exercised here —
 * BranchLookup is injected as a seam so this test proves the duplication
 * logic (one independent Menu+Category+Product copy per branch) without a
 * running HTTP stack.
 */
class BranchMenuBackfillRunnerTest {

    private MenuRepository menuRepository;
    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        menuRepository = mock(MenuRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
    }

    @Test
    void eachBranchGetsAnIndependentCopyOfTheMerchantsExistingCatalog() {
        CategoryEntity existingCategory = CategoryEntity.builder()
                .id(1L).merchantId(MERCHANT).name("Drinks").displayOrder(0).build();
        ProductEntity existingProduct = ProductEntity.builder()
                .id(1L).merchantId(MERCHANT).categoryId(1L).name("Espresso")
                .price(BigDecimal.TEN).available(true).preparationTime(3).build();

        when(categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(MERCHANT))
                .thenReturn(List.of(existingCategory));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(existingProduct));
        when(menuRepository.findByBranchId(any())).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> {
            MenuEntity m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchMenuBackfillRunner runner = new BranchMenuBackfillRunner(
                menuRepository, categoryRepository, productRepository);

        runner.backfillMerchant(MERCHANT, List.of(10L, 11L));

        // One Menu per branch, both PUBLISHED (existing catalogs are already
        // always-live today — publishing nothing new does not change
        // customer-visible behavior at cutover, per the spec).
        verify(menuRepository, times(2)).save(argThat(m -> m.getStatus() == MenuEntity.Status.PUBLISHED));
        // One independent category copy per branch (2 branches -> 2 saves), not one shared row.
        verify(categoryRepository, times(2)).save(any());
        verify(productRepository, times(2)).save(any());
    }

    @Test
    void aBranchThatAlreadyHasAMenuIsSkipped() {
        when(menuRepository.findByBranchId(10L)).thenReturn(
                Optional.of(MenuEntity.builder().id(UUID.randomUUID()).branchId(10L).build()));
        when(categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(MERCHANT)).thenReturn(List.of());

        BranchMenuBackfillRunner runner = new BranchMenuBackfillRunner(
                menuRepository, categoryRepository, productRepository);

        runner.backfillMerchant(MERCHANT, List.of(10L));

        // Idempotent: re-running the job must not duplicate an already-backfilled branch.
        verify(menuRepository, never()).save(any());
    }
}
