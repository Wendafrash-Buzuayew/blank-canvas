package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuServiceCategoryTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository);
    }

    @Test
    void getOrCreateMenuForBranchCreatesADraftMenuOnFirstUse() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertEquals(BRANCH, menu.getBranchId());
        assertEquals(MERCHANT, menu.getMerchantId());
        assertEquals(MenuEntity.Status.DRAFT, menu.getStatus());
    }

    @Test
    void getOrCreateMenuForBranchIsIdempotent() {
        MenuEntity existing = MenuEntity.builder().branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(existing));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertSame(existing, menu);
        verify(menuRepository, never()).save(any());
    }

    @Test
    void createCategoryAttachesItToTheBranchsMenu() {
        MenuEntity menu = MenuEntity.builder().id(UUID.randomUUID()).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryEntity category = service.createCategory(CreateCategoryRequest.builder()
                .branchId(BRANCH).merchantId(MERCHANT).name("Drinks").build());

        assertEquals(menu.getId(), category.getMenuId());
        assertEquals(MERCHANT, category.getMerchantId());
    }
}
