package com.qrserve.menu.service;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuPublishTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MENU_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository);
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

        MenuEntity published = service.publish(BRANCH);

        assertEquals(MenuEntity.Status.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void publishRefusesABranchWithNoMenuYet() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.publish(BRANCH));
    }
}
