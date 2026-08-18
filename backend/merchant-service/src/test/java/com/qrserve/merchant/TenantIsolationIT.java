package com.qrserve.merchant;

import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.service.MerchantEventPublisher;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tenant isolation gate.
 *
 * <p>QRServe is a shared multi-tenant deployment: one set of services, many
 * restaurants, separated only by {@code merchantId}. An isolation defect here is
 * not an internal inconsistency — it is one paying customer reading another's
 * revenue. Six such holes were found and fixed on this branch, which is reason
 * enough to treat isolation as a CI gate rather than a review habit.
 *
 * <p>Every assertion is stated as "merchant A's credential must not reach
 * merchant B's data". New tenant-scoped endpoints belong here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TenantIsolationIT {

    static final String MERCHANT_A_NAME = "Sunrise Coffee";
    static final String MERCHANT_B_NAME = "Blue Nile Restaurant";

    /**
     * MockMvc is built by hand rather than injected via {@code @AutoConfigureMockMvc}.
     * Spring Boot 4 moved that annotation into a separate
     * {@code spring-boot-webmvc-test-autoconfigure} module which this project does
     * not depend on, and building it here needs only {@code spring-test} plus
     * {@code spring-security-test}, both already present.
     *
     * <p>It also makes the important part explicit: {@code springSecurity()} installs
     * the real filter chain. Without it every assertion below would exercise the
     * controller with no authorization at all and pass for the wrong reason.
     */
    @Autowired
    WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    MerchantRepository merchantRepository;
    @Autowired
    BranchRepository branchRepository;
    @Autowired
    TableRepository tableRepository;

    /**
     * Kafka is not running under test and {@code KafkaTemplate.send} would block
     * trying to reach a broker. No assertion here concerns events.
     */
    @MockitoBean
    MerchantEventPublisher merchantEventPublisher;

    MerchantEntity merchantA;
    MerchantEntity merchantB;
    BranchEntity branchA;
    BranchEntity branchB;
    TableEntity tableA;
    TableEntity tableB;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        tableRepository.deleteAll();
        branchRepository.deleteAll();
        merchantRepository.deleteAll();

        merchantA = merchantRepository.save(merchant(MERCHANT_A_NAME, "sunrise"));
        merchantB = merchantRepository.save(merchant(MERCHANT_B_NAME, "blue-nile"));

        // Both tenants deliberately name their branch "Main". Under the old
        // globally-unique branch slug the second save throws a constraint
        // violation, which is the whole point of the schema change in Task 5.
        branchA = branchRepository.save(branch(merchantA.getId(), "Main", "main"));
        branchB = branchRepository.save(branch(merchantB.getId(), "Main", "main"));

        tableA = tableRepository.save(table(merchantA.getId(), branchA.getId(), "1", "qr-a-1"));
        tableB = tableRepository.save(table(merchantB.getId(), branchB.getId(), "1", "qr-b-1"));
    }

    private MerchantEntity merchant(String name, String slug) {
        return MerchantEntity.builder()
                .name(name).slug(slug).phone("+251900000000")
                .city("Addis Ababa").address("Bole").category("CAFE")
                .build();
    }

    private BranchEntity branch(UUID merchantId, String name, String slug) {
        return BranchEntity.builder()
                .merchantId(merchantId).name(name).slug(slug)
                .phone("+251900000000").address("Bole")
                .build();
    }

    private TableEntity table(UUID merchantId, Long branchId, String number, String qrToken) {
        return TableEntity.builder()
                .merchantId(merchantId).branchId(branchId).tableNumber(number)
                .capacity(4).status("AVAILABLE").qrToken(qrToken)
                .build();
    }

    /** A bearer header value, {@code Bearer } prefix included. */
    String tokenFor(UUID merchantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(role.name().toLowerCase() + "@" + merchantId + ".test")
                .role(role)
                .build();
        return "Bearer " + jwtTokenProvider.generateAccessToken(principal);
    }

    // ---- GET /api/merchants/{id} ----

    @Test
    @DisplayName("an owner cannot read another merchant's profile")
    void ownerCannotReadForeignMerchant() throws Exception {
        mockMvc.perform(get("/api/merchants/" + merchantB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an owner can read their own merchant profile")
    void ownerCanReadOwnMerchant() throws Exception {
        mockMvc.perform(get("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(MERCHANT_A_NAME));
    }

    // ---- GET /api/branches/merchant/{merchantId} ----

    @Test
    @DisplayName("an owner cannot list another merchant's branches")
    void ownerCannotListForeignBranches() throws Exception {
        mockMvc.perform(get("/api/branches/merchant/" + merchantB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isForbidden());
    }

    // ---- GET /api/tables/all ----

    @Test
    @DisplayName("the table list is pinned to the caller's tenant even when it asks for another")
    void tableListIsPinnedToCallerTenant() throws Exception {
        // The merchantId query parameter is attacker-controlled. Only SUPER_ADMIN
        // may steer it; everyone else is pinned to their own tenant regardless.
        mockMvc.perform(get("/api/tables/all")
                        .param("merchantId", merchantB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].merchantId").value(merchantA.getId().toString()));
    }

    @Test
    @DisplayName("the table list is not anonymously readable")
    void tableListRequiresAuthentication() throws Exception {
        // "/api/tables/*" is a public GET rule and it also matches "/all"; an
        // explicit authenticated rule must sit above it.
        mockMvc.perform(get("/api/tables/all"))
                .andExpect(status().is4xxClientError());
    }

    // ---- public menu resolution ----

    @Test
    @DisplayName("both tenants can have a branch called Main and each resolves to its own")
    void publicMenuResolvesPerTenant() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/sunrise/main/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()))
                .andExpect(jsonPath("$.branchId").value(branchA.getId()));

        mockMvc.perform(get("/api/v1/public/menu/blue-nile/main/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantB.getId().toString()))
                .andExpect(jsonPath("$.branchId").value(branchB.getId()));
    }

    @Test
    @DisplayName("an unknown merchant slug is 404, never a fallback to some default tenant")
    void unknownSlugIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/no-such-tenant/main/1"))
                .andExpect(status().isNotFound());
    }
}
