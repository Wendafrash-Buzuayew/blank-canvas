package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional
    @CacheEvict(value = "menus", key = "#request.merchantId")
    public CategoryEntity createCategory(CreateCategoryRequest request) {
        CategoryEntity category = CategoryEntity.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = "menus", key = "#result.merchantId")
    public ProductEntity createProduct(CreateProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        ProductEntity product = ProductEntity.builder()
                .merchantId(category.getMerchantId())
                .categoryId(category.getId())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .image(request.getImage())
                .available(true)
                .preparationTime(request.getPreparationTime() != null ? request.getPreparationTime() : 15)
                .build();

        return productRepository.save(product);
    }

    @Cacheable(value = "menus", key = "#merchantId")
    public MenuResponse getFullMenu(UUID merchantId) {
        List<CategoryEntity> categories = categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(merchantId);

        List<MenuResponse.CategoryDto> categoryDtos = categories.stream().map(cat -> {
            List<ProductEntity> products = productRepository.findByCategoryId(cat.getId());

            List<MenuResponse.ProductDto> productDtos = products.stream().map(prod ->
                    MenuResponse.ProductDto.builder()
                            .id(prod.getId())
                            .name(prod.getName())
                            .description(prod.getDescription())
                            .price(prod.getPrice())
                            .image(prod.getImage())
                            .available(prod.isAvailable())
                            .preparationTime(prod.getPreparationTime())
                            .build()
            ).collect(Collectors.toList());

            return MenuResponse.CategoryDto.builder()
                    .id(cat.getId())
                    .name(cat.getName())
                    .items(productDtos)
                    .build();
        }).collect(Collectors.toList());

        return MenuResponse.builder()
                .categories(categoryDtos)
                .build();
    }
}
