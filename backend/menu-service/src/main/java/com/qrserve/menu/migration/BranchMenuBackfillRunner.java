package com.qrserve.menu.migration;

import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-off, opt-in migration: every existing branch gets its own independent
 * copy of its merchant's current (always-live, merchantId-scoped) catalog.
 * Disabled by default ({@code backfill.branch-menus.enabled=false}); flip it
 * on for exactly one deploy, then back off. See
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md
 * Section 5 for why this is code, not a Flyway/SQL script: generating new
 * ids and remapping category->product foreign keys per branch is far safer
 * to get right — and to unit test — here than in {@code INSERT ... SELECT}.
 */
@Component
@Slf4j
public class BranchMenuBackfillRunner implements CommandLineRunner {

    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // Optional constructor-injected collaborators for the full run() path;
    // the no-HTTP constructor below is what the unit test above uses.
    private RestTemplate restTemplate;
    private JwtTokenProvider jwtTokenProvider;
    private String merchantServiceUrl;
    private boolean enabled;
    // Built explicitly from an injected PlatformTransactionManager rather than
    // relying on @Transactional here: backfillMerchant is called from run()
    // via plain `this.` self-invocation, which bypasses Spring's transactional
    // AOP proxy entirely — an @Transactional annotation on a self-invoked
    // method is silently inert. TransactionTemplate applies the transaction
    // boundary explicitly at the call site instead, so it works regardless of
    // proxying, and stays unit-testable with a mocked PlatformTransactionManager.
    private TransactionTemplate transactionTemplate;

    public BranchMenuBackfillRunner(MenuRepository menuRepository, CategoryRepository categoryRepository,
                                     ProductRepository productRepository) {
        this.menuRepository = menuRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Autowired
    public BranchMenuBackfillRunner(
            MenuRepository menuRepository, CategoryRepository categoryRepository,
            ProductRepository productRepository, RestTemplate restTemplate,
            JwtTokenProvider jwtTokenProvider, PlatformTransactionManager transactionManager,
            @Value("${services.merchant-service-url:http://localhost:8085}") String merchantServiceUrl,
            @Value("${backfill.branch-menus.enabled:false}") boolean enabled) {
        this(menuRepository, categoryRepository, productRepository);
        this.restTemplate = restTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.merchantServiceUrl = merchantServiceUrl;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        log.info("Branch-menu backfill starting");
        HttpHeaders authHeaders = serviceAuthHeaders();

        List<UUID> merchantIds = fetchAllMerchantIds(authHeaders);
        for (UUID merchantId : merchantIds) {
            List<Long> branchIds = fetchBranchIds(merchantId, authHeaders);
            ensurePrimaryBranch(merchantId, branchIds, authHeaders);
            // Explicit transaction boundary — see the transactionTemplate field's
            // Javadoc for why @Transactional on backfillMerchant itself would be inert.
            transactionTemplate.execute(status -> {
                backfillMerchant(merchantId, branchIds);
                return null;
            });
        }
        log.info("Branch-menu backfill complete: {} merchants processed", merchantIds.size());
    }

    /**
     * Without a primary branch, /m/{merchant-slug} (no branch segment) 404s
     * for every merchant that existed before this migration ran — defeating
     * the point of the redesign for pre-existing merchants. Designates the
     * first branch as primary via merchant-service's own
     * PATCH /api/branches/{id}/primary endpoint (this runner doesn't own
     * BranchEntity, so it can't set is_primary directly via JPA).
     */
    @SuppressWarnings("unchecked")
    private void ensurePrimaryBranch(UUID merchantId, List<Long> branchIds, HttpHeaders headers) {
        if (branchIds.isEmpty()) {
            return;
        }
        String url = merchantServiceUrl + "/api/branches/merchant/" + merchantId;
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> branches = response.getBody();
        // Jackson serializes BranchEntity#isPrimary (a `boolean isPrimary` field, so
        // Lombok's getter is still isPrimary()) as JSON key "primary", not "isPrimary".
        boolean hasPrimary = branches != null && branches.stream()
                .anyMatch(b -> Boolean.TRUE.equals(b.get("primary")));
        if (!hasPrimary) {
            Long firstBranchId = branchIds.get(0);
            String patchUrl = merchantServiceUrl + "/api/branches/" + firstBranchId + "/primary";
            restTemplate.exchange(patchUrl, HttpMethod.PATCH, new HttpEntity<>(headers), Void.class);
        }
    }

    /**
     * The pure, HTTP-free part — one independent Menu (+ category/product
     * copy) per branch, skipping any branch that already has one. Public so
     * it's directly unit-testable without standing up REST calls.
     * Wrapped per-merchant in a TransactionTemplate at the run() call site
     * (not an @Transactional annotation here — see that field's Javadoc): a
     * crash partway through this merchant's multi-branch backfill rolls back
     * cleanly, leaving it eligible for a clean re-run (the
     * findByBranchId(...).isPresent() skip above already makes re-running
     * safe); other merchants already committed in the run() loop are
     * unaffected since each merchant is its own transaction.
     */
    public void backfillMerchant(UUID merchantId, List<Long> branchIds) {
        List<CategoryEntity> sourceCategories =
                categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(merchantId);

        for (Long branchId : branchIds) {
            if (menuRepository.findByBranchId(branchId).isPresent()) {
                continue; // already backfilled — idempotent re-run
            }

            MenuEntity menu = menuRepository.save(MenuEntity.builder()
                    .branchId(branchId)
                    .merchantId(merchantId)
                    .status(MenuEntity.Status.PUBLISHED)
                    // Matches MenuService.publish(): status PUBLISHED always carries a
                    // publishedAt, so downstream readers of that field see a consistent value
                    // regardless of whether a branch reached PUBLISHED via the explicit publish
                    // endpoint or via this backfill.
                    .publishedAt(java.time.LocalDateTime.now())
                    .templateStyle(MenuEntity.TemplateStyle.CLASSIC)
                    .build());

            for (CategoryEntity source : sourceCategories) {
                CategoryEntity copy = categoryRepository.save(CategoryEntity.builder()
                        .menuId(menu.getId())
                        .merchantId(merchantId)
                        .name(source.getName())
                        .displayOrder(source.getDisplayOrder())
                        .build());

                for (ProductEntity sourceProduct : productRepository.findByCategoryId(source.getId())) {
                    productRepository.save(ProductEntity.builder()
                            .menuId(menu.getId())
                            .merchantId(merchantId)
                            .categoryId(copy.getId())
                            .name(sourceProduct.getName())
                            .description(sourceProduct.getDescription())
                            .price(sourceProduct.getPrice())
                            .image(sourceProduct.getImage())
                            .available(sourceProduct.isAvailable())
                            .preparationTime(sourceProduct.getPreparationTime())
                            .build());
                }
            }
        }
    }

    private HttpHeaders serviceAuthHeaders() {
        UserPrincipal serviceIdentity = UserPrincipal.builder().role(UserRole.SUPER_ADMIN).build();
        String token = jwtTokenProvider.generateInternalServiceToken(serviceIdentity);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> fetchAllMerchantIds(HttpHeaders headers) {
        String url = merchantServiceUrl + "/api/merchants";
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body.stream()
                .map(m -> UUID.fromString((String) m.get("id")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchBranchIds(UUID merchantId, HttpHeaders headers) {
        String url = merchantServiceUrl + "/api/branches/merchant/" + merchantId;
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body.stream()
                .map(b -> ((Number) b.get("id")).longValue())
                .toList();
    }
}
