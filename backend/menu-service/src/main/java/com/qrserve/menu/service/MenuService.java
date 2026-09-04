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
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MenuRepository menuRepository;
    private final RestTemplate restTemplate;
    private final PlatformTransactionManager transactionManager;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

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

    /**
     * Not {@code @Transactional}: fetchBranchMerchantId is a synchronous
     * outbound HTTP call to merchant-service, and opening a DB transaction
     * before it returns would hold a pooled connection for the full
     * round-trip. The ownership check runs first, with no transaction open;
     * only the actual DB writes run inside one, via runInTransaction.
     */
    @CacheEvict(value = "menus", key = "#merchantId")
    public CategoryEntity createCategory(CreateCategoryRequest request, UUID merchantId) {
        UUID actualMerchantId = fetchBranchMerchantId(request.getBranchId());
        if (!actualMerchantId.equals(merchantId)) {
            throw new AccessDeniedException("Branch " + request.getBranchId() + " does not belong to your merchant");
        }
        return runInTransaction(() -> {
            MenuEntity menu = getOrCreateMenuForBranch(request.getBranchId(), merchantId);
            CategoryEntity category = CategoryEntity.builder()
                    .menuId(menu.getId())
                    .merchantId(merchantId)
                    .name(request.getName())
                    .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                    .build();
            return categoryRepository.save(category);
        });
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

    /** Menu-scoped variant, used by the new branch-level public endpoint (Task 8). */
    public MenuResponse getFullMenuByMenuId(UUID menuId) {
        List<CategoryEntity> categories = categoryRepository.findByMenuIdOrderByDisplayOrderAsc(menuId);

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

        return MenuResponse.builder().categories(categoryDtos).build();
    }

    /** Used by the public branch-menu endpoint (Task 8) to look up a branch's menu and its status. */
    public Optional<MenuEntity> getMenuForBranch(Long branchId) {
        return menuRepository.findByBranchId(branchId);
    }

    /**
     * Authenticated, draft-inclusive counterpart to getMenuForBranch/
     * getFullMenuByMenuId: the merchant's own menu-builder UI must see its
     * own unpublished work, unlike the public digital-menu endpoint (Task 8)
     * which 404s anything not PUBLISHED. A branch with no menu yet (no
     * category ever added) returns an empty menu rather than 404 — that's a
     * normal, not-yet-started state for the builder UI, not an error.
     */
    public MenuResponse getMenuForBranchManagement(Long branchId, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() != UserRole.SUPER_ADMIN) {
            UUID actualMerchantId = fetchBranchMerchantId(branchId);
            if (!actualMerchantId.equals(principal.getMerchantId())) {
                throw new AccessDeniedException("Branch " + branchId + " does not belong to your merchant");
            }
        }
        return menuRepository.findByBranchId(branchId)
                .map(menu -> getFullMenuByMenuId(menu.getId()))
                .orElseGet(() -> MenuResponse.builder().categories(List.of()).build());
    }

    /**
     * Publish is the HLD's own gate: "Only menus with a Published status are
     * made available through the public menu interface" (6.2). Requires a
     * menu to already exist for the branch — call getOrCreateMenuForBranch
     * (via adding a category) before this.
     */
    /** Not {@code @Transactional} — see createCategory's Javadoc for why. */
    public MenuEntity publish(Long branchId, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() != UserRole.SUPER_ADMIN) {
            UUID actualMerchantId = fetchBranchMerchantId(branchId);
            if (!actualMerchantId.equals(principal.getMerchantId())) {
                throw new AccessDeniedException("Branch " + branchId + " does not belong to your merchant");
            }
        }
        return runInTransaction(() -> {
            MenuEntity menu = menuRepository.findByBranchId(branchId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No menu to publish for branch " + branchId + " — add at least one category first"));
            menu.setStatus(MenuEntity.Status.PUBLISHED);
            menu.setPublishedAt(java.time.LocalDateTime.now());
            return menuRepository.save(menu);
        });
    }

    /**
     * Applies a transaction boundary explicitly, in one method, regardless
     * of whether this method is entered externally (proxied) or via
     * self-invocation (bypasses the proxy) — sidesteps Spring AOP's
     * self-invocation limitation the same way BranchMenuBackfillRunner does.
     */
    private <T> T runInTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    /**
     * Verifies a caller-controlled branchId actually belongs to the expected
     * merchant, by asking merchant-service (the owner of BranchEntity) rather
     * than trusting the request body. Mirrors QrGeneratorService.fetchBranch's
     * RestTemplate + forwarded-Authorization-header pattern in qr-service.
     */
    private UUID fetchBranchMerchantId(Long branchId) {
        try {
            String url = merchantServiceUrl + "/api/branches/" + branchId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(getAuthHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Branch not found: " + branchId);
            }
            return UUID.fromString((String) body.get("merchantId"));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify branch {} ownership via merchant-service", branchId, e);
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
    }

    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, authToken);
            }
        }
        return headers;
    }
}