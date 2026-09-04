package com.qrserve.menu.migration;

import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
        // Assign a fresh, distinct id per saved category — like a real IDENTITY column
        // would — so the cross-reference assertions below (product.categoryId ==
        // this branch's own category copy id) are actually meaningful; without this,
        // every mocked save would leave id null and a categoryId-aliasing bug would go
        // undetected (null would spuriously "match" every branch).
        AtomicLong categoryIdSeq = new AtomicLong(100);
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            CategoryEntity c = inv.getArgument(0);
            c.setId(categoryIdSeq.incrementAndGet());
            return c;
        });
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

        // The counts above would still pass under a plausible aliasing bug (e.g. reusing
        // the same CategoryEntity instance across both branches instead of building a
        // fresh one each time) — call counts don't change even if cross-references
        // silently break. Capture the actual saved entities and assert on the real
        // cross-references to guard against exactly that.
        ArgumentCaptor<MenuEntity> menuCaptor = ArgumentCaptor.forClass(MenuEntity.class);
        verify(menuRepository, times(2)).save(menuCaptor.capture());
        ArgumentCaptor<CategoryEntity> categoryCaptor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryRepository, times(2)).save(categoryCaptor.capture());
        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository, times(2)).save(productCaptor.capture());

        List<MenuEntity> savedMenus = menuCaptor.getAllValues();
        List<CategoryEntity> savedCategories = categoryCaptor.getAllValues();
        List<ProductEntity> savedProducts = productCaptor.getAllValues();

        MenuEntity menuForBranch10 = savedMenus.stream()
                .filter(m -> m.getBranchId().equals(10L)).findFirst().orElseThrow();
        MenuEntity menuForBranch11 = savedMenus.stream()
                .filter(m -> m.getBranchId().equals(11L)).findFirst().orElseThrow();
        assertNotEquals(menuForBranch10.getId(), menuForBranch11.getId());

        // Each branch's category copy is tied to THAT branch's own menu — not shared,
        // not swapped with the other branch's menu.
        CategoryEntity categoryForBranch10 = savedCategories.stream()
                .filter(c -> c.getMenuId().equals(menuForBranch10.getId())).findFirst().orElseThrow();
        CategoryEntity categoryForBranch11 = savedCategories.stream()
                .filter(c -> c.getMenuId().equals(menuForBranch11.getId())).findFirst().orElseThrow();
        assertNotEquals(categoryForBranch10.getId(), categoryForBranch11.getId());
        assertNotSame(categoryForBranch10, categoryForBranch11);

        // Each branch's product copy points at THAT branch's own category copy — not the
        // original source category (id 1L) and not the other branch's copy.
        ProductEntity productForBranch10 = savedProducts.stream()
                .filter(p -> p.getCategoryId().equals(categoryForBranch10.getId())).findFirst().orElseThrow();
        ProductEntity productForBranch11 = savedProducts.stream()
                .filter(p -> p.getCategoryId().equals(categoryForBranch11.getId())).findFirst().orElseThrow();
        assertNotEquals(existingCategory.getId(), productForBranch10.getCategoryId());
        assertNotEquals(existingCategory.getId(), productForBranch11.getCategoryId());
        assertNotEquals(productForBranch10.getCategoryId(), productForBranch11.getCategoryId());
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
