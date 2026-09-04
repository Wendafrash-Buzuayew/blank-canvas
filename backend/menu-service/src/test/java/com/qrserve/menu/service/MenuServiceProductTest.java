package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuServiceProductTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuService service;
    private static final Long CATEGORY_ID = 3L;
    private static final UUID MENU_ID = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new MenuService(categoryRepository, productRepository, mock(MenuRepository.class),
                mock(RestTemplate.class));
    }

    @Test
    void createProductInheritsMenuIdFromItsCategory() {
        CategoryEntity category = CategoryEntity.builder()
                .id(CATEGORY_ID).menuId(MENU_ID).merchantId(MERCHANT).name("Drinks").build();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductEntity product = service.createProduct(CreateProductRequest.builder()
                .categoryId(CATEGORY_ID).name("Espresso").price(BigDecimal.TEN).build());

        assertEquals(MENU_ID, product.getMenuId());
        assertEquals(MERCHANT, product.getMerchantId());
    }
}
