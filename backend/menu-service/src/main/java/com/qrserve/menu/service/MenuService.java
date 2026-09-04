package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.dto.UpdateCategoryRequest;
import com.qrserve.menu.dto.UpdateProductRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
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
    private final MenuRepository menuRepository;

    // ============ Category CRUD ============

    /**
     * A branch without a menu is a branch nobody can publish, so this is
     * called both when a category is first added to a branch and by the
     * publish endpoint (Task 5) — idempotent either way.
     */
    @Transactional
    public MenuEntity getOrCreateMenuForBranch(Long branchId, UUID merchantId) {
        return menuRepository.findByBranchId(branchId)
                .orElseGet(() -> menuRepository.save(MenuEntity.builder()
                        .branchId(branchId)
                        .merchantId(merchantId)
                        // Explicit rather than relying on MenuEntity#prePersist: that
                        // callback only fires through real JPA persistence, not when
                        // menuRepository.save is mocked (see MenuServiceCategoryTest).
                        .status(MenuEntity.Status.DRAFT)
                        .build()));
    }

    @Transactional
    @CacheEvict(value = "menus", key = "#request.merchantId")
    public CategoryEntity createCategory(CreateCategoryRequest request) {
        MenuEntity menu = getOrCreateMenuForBranch(request.getBranchId(), request.getMerchantId());
        CategoryEntity category = CategoryEntity.builder()
                .menuId(menu.getId())
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> getCategories(UUID merchantId) {
        return categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(merchantId);
    }

    @Transactional
    @CacheEvict(value = "menus", key = "#result.merchantId")
    public CategoryEntity updateCategory(Long id, UpdateCategoryRequest request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        category.setName(request.getName());
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = "menus", allEntries = true)
    public void deleteCategory(Long id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        // Delete/cascade products in this category
        productRepository.deleteByCategoryId(id);
        categoryRepository.delete(category);
    }

    // ============ Product CRUD ============

    @Transactional
    @CacheEvict(value = "menus", key = "#result.merchantId")
    public ProductEntity createProduct(CreateProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        ProductEntity product = ProductEntity.builder()
                .menuId(category.getMenuId())
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

    @Transactional(readOnly = true)
    public List<ProductEntity> getProducts(Long categoryId, UUID merchantId) {
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId);
        }
        if (merchantId != null) {
            return productRepository.findByMerchantId(merchantId);
        }
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
    }

    @Transactional
    @CacheEvict(value = "menus", allEntries = true)
    public ProductEntity updateProduct(Long id, UpdateProductRequest request) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getImage() != null) product.setImage(request.getImage());
        if (request.getAvailable() != null) product.setAvailable(request.getAvailable());
        if (request.getPreparationTime() != null) product.setPreparationTime(request.getPreparationTime());

        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "menus", allEntries = true)
    public void deleteProduct(Long id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        productRepository.delete(product);
    }

    // ============ Full Menu ============

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