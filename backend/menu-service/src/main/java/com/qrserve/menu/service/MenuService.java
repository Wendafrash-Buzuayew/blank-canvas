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
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.menu.storage.MediaStorageService;
import com.qrserve.shared.exceptions.BusinessException;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MenuRepository menuRepository;
    private final RestTemplate restTemplate;
    private final PlatformTransactionManager transactionManager;
    private final MediaStorageService mediaStorageService;
    private final MenuTemplateRepository menuTemplateRepository;

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
                        .templateStyle("CLASSIC")
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
        validateDiscount(request.getPrice(), request.getDiscountPrice(), request.getDiscountStartAt(), request.getDiscountEndAt());

        ProductEntity product = ProductEntity.builder()
                .menuId(category.getMenuId())
                .merchantId(category.getMerchantId())
                .categoryId(category.getId())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .discountStartAt(request.getDiscountStartAt())
                .discountEndAt(request.getDiscountEndAt())
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

        if (Boolean.TRUE.equals(request.getClearDiscount())) {
            product.setDiscountPrice(null);
            product.setDiscountStartAt(null);
            product.setDiscountEndAt(null);
        } else {
            if (request.getDiscountPrice() != null) product.setDiscountPrice(request.getDiscountPrice());
            if (request.getDiscountStartAt() != null) product.setDiscountStartAt(request.getDiscountStartAt());
            if (request.getDiscountEndAt() != null) product.setDiscountEndAt(request.getDiscountEndAt());
            validateDiscount(product.getPrice(), product.getDiscountPrice(), product.getDiscountStartAt(), product.getDiscountEndAt());
        }

        return productRepository.save(product);
    }

    /**
     * Uploads a real image for a product, replacing whatever was in its
     * `image` field (a hardcoded preset URL or a merchant-typed URL) with
     * one served back by MediaStorageService. The key embeds the product id
     * and a random suffix so re-uploads never collide with (or silently
     * overwrite) a previous image still cached by a client.
     */
    @Transactional
    @CacheEvict(value = "menus", allEntries = true)
    public ProductEntity updateProductImage(Long id, MultipartFile file) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        validateImageFile(file);

        String extension = extensionFor(file.getContentType());
        String key = "products/" + id + "-" + UUID.randomUUID() + extension;
        String url;
        try {
            url = mediaStorageService.store(key, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("Failed to read uploaded file", e);
        }

        product.setImage(url);
        return productRepository.save(product);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Unsupported image type: " + file.getContentType()
                    + " (allowed: " + ALLOWED_IMAGE_TYPES + ")");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * A discount price that is not strictly less than the regular price isn't
     * a promotion, and an end before its own start can never be active — both
     * are merchant input errors worth rejecting rather than silently
     * accepting a discount that would never apply or would overcharge.
     */
    private void validateDiscount(BigDecimal price, BigDecimal discountPrice,
                                   LocalDateTime discountStartAt, LocalDateTime discountEndAt) {
        if (discountPrice == null) {
            return;
        }
        if (discountPrice.compareTo(price) >= 0) {
            throw new BusinessException("discountPrice must be less than price");
        }
        if (discountStartAt != null && discountEndAt != null && !discountEndAt.isAfter(discountStartAt)) {
            throw new BusinessException("discountEndAt must be after discountStartAt");
        }
    }

    /**
     * The price to actually display/charge at instant `now`: discounted only
     * while a discount price is configured AND `now` falls within its
     * window. An unset bound is open-ended in that direction; both unset
     * means the discount is active for as long as discountPrice is set —
     * an indefinite promotion the merchant turns on/off explicitly. Takes
     * `now` as a parameter (rather than calling LocalDateTime.now() itself)
     * so it is deterministic and unit-testable without mocking the clock.
     */
    static BigDecimal effectivePrice(ProductEntity product, LocalDateTime now) {
        if (product.getDiscountPrice() == null) {
            return product.getPrice();
        }
        boolean startedOrUnbounded = product.getDiscountStartAt() == null || !now.isBefore(product.getDiscountStartAt());
        boolean notYetEndedOrUnbounded = product.getDiscountEndAt() == null || !now.isAfter(product.getDiscountEndAt());
        return (startedOrUnbounded && notYetEndedOrUnbounded) ? product.getDiscountPrice() : product.getPrice();
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
        return toMenuResponse(categories);
    }

    /** Menu-scoped variant, used by the new branch-level public endpoint (Task 8). */
    public MenuResponse getFullMenuByMenuId(UUID menuId) {
        List<CategoryEntity> categories = categoryRepository.findByMenuIdOrderByDisplayOrderAsc(menuId);
        return toMenuResponse(categories);
    }

    /**
     * Shared category/product -> DTO mapping used by both getFullMenu and
     * getFullMenuByMenuId (previously duplicated verbatim between them).
     * `now` is captured once per call so every product's discount-window
     * check in a given response uses the same instant, rather than each
     * product independently sampling the clock.
     */
    private MenuResponse toMenuResponse(List<CategoryEntity> categories) {
        LocalDateTime now = LocalDateTime.now();

        List<MenuResponse.CategoryDto> categoryDtos = categories.stream().map(cat -> {
            List<ProductEntity> products = productRepository.findByCategoryId(cat.getId());

            List<MenuResponse.ProductDto> productDtos = products.stream().map(prod ->
                    MenuResponse.ProductDto.builder()
                            .id(prod.getId())
                            .name(prod.getName())
                            .description(prod.getDescription())
                            .price(prod.getPrice())
                            .effectivePrice(effectivePrice(prod, now))
                            .discountPrice(prod.getDiscountPrice())
                            .discountStartAt(prod.getDiscountStartAt())
                            .discountEndAt(prod.getDiscountEndAt())
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
                .map(menu -> {
                    MenuResponse response = getFullMenuByMenuId(menu.getId());
                    response.setTemplateStyle(menu.getTemplateStyle());
                    return response;
                })
                .orElseGet(() -> MenuResponse.builder()
                        .templateStyle("CLASSIC")
                        .categories(List.of())
                        .build());
    }

    /**
     * Sets the branch's curated visual template, auto-creating its menu if
     * this is the first time anything has been configured for it — a
     * merchant should be able to pick a look before adding a single
     * category. Same tenant-ownership check as publish/createCategory.
     * templateStyle is validated against the live template-definitions
     * table (not a compile-time enum) since admins can create/delete
     * definitions freely — see MenuTemplateService.
     */
    public MenuEntity setTemplate(Long branchId, String templateStyle, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UUID merchantId;
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            merchantId = fetchBranchMerchantId(branchId);
        } else {
            merchantId = principal.getMerchantId();
            UUID actualMerchantId = fetchBranchMerchantId(branchId);
            if (!actualMerchantId.equals(merchantId)) {
                throw new AccessDeniedException("Branch " + branchId + " does not belong to your merchant");
            }
        }
        if (!menuTemplateRepository.existsById(templateStyle)) {
            throw new ResourceNotFoundException("No template definition with key: " + templateStyle);
        }
        UUID resolvedMerchantId = merchantId;
        return runInTransaction(() -> {
            MenuEntity menu = getOrCreateMenuForBranch(branchId, resolvedMerchantId);
            menu.setTemplateStyle(templateStyle);
            return menuRepository.save(menu);
        });
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