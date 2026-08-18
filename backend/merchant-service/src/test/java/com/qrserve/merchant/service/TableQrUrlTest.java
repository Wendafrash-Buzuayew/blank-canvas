package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The QR URL is the product. If it is wrong, a restaurant prints table stands
 * that do not work and finds out from a confused customer — so this asserts the
 * exact string rather than smoke-testing the endpoint.
 */
class TableQrUrlTest {

    private static final UUID MERCHANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private TableService tableService;
    private QrSignatureService signatures;

    @BeforeEach
    void setUp() {
        TableRepository tableRepository = mock(TableRepository.class);
        BranchRepository branchRepository = mock(BranchRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        signatures = new QrSignatureService("master-secret-value", "");
        PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");

        when(branchRepository.findById(7L)).thenReturn(Optional.of(BranchEntity.builder()
                .id(7L).merchantId(MERCHANT_ID).name("Main").slug("main")
                .phone("+251900000000").address("Bole").build()));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(MerchantEntity.builder()
                .id(MERCHANT_ID).name("Sunrise Coffee").slug("sunrise")
                .phone("+251900000000").city("Addis Ababa").address("Bole").category("CAFE").build()));
        // save() assigns the id the database would have assigned.
        when(tableRepository.save(any(TableEntity.class))).thenAnswer(inv -> {
            TableEntity t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        tableService = new TableService(tableRepository, branchRepository, merchantRepository, urls, signatures);
    }

    private CreateTableResponse createTable(String tableNumber) {
        CreateTableRequest request = new CreateTableRequest();
        request.setBranchId(7L);
        request.setTableNumber(tableNumber);
        request.setCapacity(4);
        return tableService.createTable(request);
    }

    private String signatureOf(String qrUrl) {
        int idx = qrUrl.indexOf("?signature=");
        assertTrue(idx > 0, "a printed code must be signed, otherwise the signature check is dead code");
        return qrUrl.substring(idx + "?signature=".length());
    }

    @Test
    @DisplayName("the merchant is the host label and the branch SLUG is the path")
    void urlUsesHostAndBranchSlug() {
        String qrUrl = createTable("12").getQrUrl();
        URI uri = URI.create(qrUrl);

        // The old output was https://qrserve.com/menu/sunrise/7/42 - hardcoded host,
        // branch ID where the resolver expects a slug, table ID where it expects a
        // table number. Three mismatches, one 404.
        assertEquals("sunrise.qrserve.safaricom.et", uri.getHost());
        assertEquals("/menu/main/12", uri.getPath());
        assertTrue(qrUrl.startsWith("https://"), "the scheme comes from configuration");
    }

    @Test
    @DisplayName("the table NUMBER is in the path, not the table id")
    void urlUsesTableNumberNotId() {
        // save() assigns id 42; the URL must carry "12". The resolver looks the table
        // up by table_number, so an id here is an immediate 404.
        assertEquals("/menu/main/12", URI.create(createTable("12").getQrUrl()).getPath());
    }

    @Test
    @DisplayName("the URL carries a signature that validates for this table")
    void urlIsSigned() {
        String signature = signatureOf(createTable("12").getQrUrl());
        assertTrue(signatures.validateSignature(signature, MERCHANT_ID, 7L, 42L),
                "the signature must cover this merchant, branch and table");
    }

    @Test
    @DisplayName("the signature does not validate for a different table")
    void signatureIsTableSpecific() {
        String signature = signatureOf(createTable("12").getQrUrl());
        assertFalse(signatures.validateSignature(signature, MERCHANT_ID, 7L, 99L),
                "moving a signed sticker to another table must not work");
    }

    @Test
    @DisplayName("a table number with a space is encoded rather than breaking the URL")
    void encodesTableNumber() {
        assertTrue(createTable("A 1").getQrUrl().contains("/menu/main/A%201"));
    }
}
