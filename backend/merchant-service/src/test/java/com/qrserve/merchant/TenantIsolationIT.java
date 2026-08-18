package com.qrserve.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.dto.CreateMerchantRequest;
import com.qrserve.merchant.dto.CreateTableRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    ObjectMapper objectMapper;
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

    // ---- branch slug: unique per merchant, taken from the request ----

    private String branchJson(UUID merchantId, String name, String slug) throws Exception {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setMerchantId(merchantId);
        request.setName(name);
        request.setSlug(slug);
        request.setPhone("+251900000000");
        request.setAddress("Bole");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("two tenants may each have a branch slug 'second'")
    void branchSlugIsUniquePerMerchantNotGlobally() throws Exception {
        // Seeding already created "main" for both tenants. This asserts a
        // tenant-scoped write also succeeds rather than colliding.
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Second Hall", "second")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantB.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantB.getId(), "Second Hall", "second")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a duplicate branch slug within one merchant is a 400, not a 500")
    void duplicateBranchSlugWithinMerchantIsRejected() throws Exception {
        // A raw constraint violation surfaces as 500 "An unexpected server error
        // occurred", which tells the owner nothing about what to change.
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Main Again", "main")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the slug supplied in the request is honoured, not silently overridden by the name")
    void suppliedBranchSlugIsHonoured() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Bole Road Terrace", "terrace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("terrace"));
    }

    @Test
    @DisplayName("a branch slug is normalised, and an unusable one is a 400")
    void branchSlugIsNormalised() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Upper Deck", "  Upper   Deck!  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("upper-deck"));

        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Bad", "!!!")))
                .andExpect(status().isBadRequest());
    }

    // ---- the generated QR URL must resolve through the endpoint it points at ----

    @Test
    @DisplayName("a generated QR URL resolves through the public endpoint it points at")
    void generatedQrUrlResolves() throws Exception {
        CreateTableRequest req = new CreateTableRequest();
        req.setBranchId(branchA.getId());
        req.setTableNumber("9");
        req.setCapacity(2);

        String body = mockMvc.perform(post("/api/tables")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String qrUrl = objectMapper.readTree(body).get("qrUrl").asText();
        java.net.URI uri = java.net.URI.create(qrUrl);
        String signature = uri.getQuery().substring("signature=".length());

        // Feed the generated URL's own path and signature straight back into the
        // resolver. This is the assertion that would have caught defect 1, and it is
        // worth more than the unit test because it crosses the generator/resolver
        // boundary - the exact seam the two copies of the format drifted across.
        mockMvc.perform(get("/api/v1/public/menu/" + merchantA.getSlug()
                        + uri.getPath().substring("/menu".length()))
                        .param("signature", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()))
                .andExpect(jsonPath("$.tableNumber").value("9"));
    }

    // ---- merchant slug: owner-supplied, validated, permanent ----

    private String merchantJson(String name, String slug) throws Exception {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName(name);
        request.setSlug(slug);
        request.setPhone("+251900000000");
        request.setCity("Addis Ababa");
        request.setAddress("Bole");
        request.setCategory("CAFE");
        return objectMapper.writeValueAsString(request);
    }

    private String superAdminToken() {
        return tokenFor(null, UserRole.SUPER_ADMIN);
    }

    @Test
    @DisplayName("a merchant is created with the slug the owner supplied")
    void merchantSlugIsOwnerSupplied() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Kaffa Roasters", "kaffa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("kaffa"))
                .andExpect(jsonPath("$.name").value("Kaffa Roasters"));
    }

    @Test
    @DisplayName("a reserved slug is rejected so no tenant can claim admin.")
    void reservedMerchantSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Admin Cafe", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a colliding slug gets a deterministic suffix rather than a constraint violation")
    void collidingMerchantSlugGetsSuffix() throws Exception {
        // "sunrise" is taken by merchantA in seed().
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Sunrise Bakery", "sunrise")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sunrise-2"));
    }

    @Test
    @DisplayName("an unusable slug is a 400 that names what to fix")
    void unusableMerchantSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("ካፈ አበባ", "ካፈ አበባ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Latin")));
    }

    @Test
    @DisplayName("the slug is permanent: an update that changes it is rejected")
    void merchantSlugCannotBeRenamed() throws Exception {
        // Renames are blocked until the alias table exists. Without this guard the
        // rename silently does nothing, which is worse than a clear refusal.
        mockMvc.perform(put("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson(MERCHANT_A_NAME, "sunrise-rebrand")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("permanent")));
    }

    @Test
    @DisplayName("an update that keeps the slug succeeds and can still change the display name")
    void merchantUpdateKeepingSlugSucceeds() throws Exception {
        // The Amharic case: the display name may be in any script, because the
        // hostname label is carried separately.
        mockMvc.perform(put("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("ካፈ አበባ", "sunrise")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ካፈ አበባ"))
                .andExpect(jsonPath("$.slug").value("sunrise"));
    }
}
