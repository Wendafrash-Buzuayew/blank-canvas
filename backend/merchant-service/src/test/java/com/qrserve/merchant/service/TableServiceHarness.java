package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wires a real {@link TableService} against mocked repositories and a mocked
 * {@link TableQrProvisioningService} / {@link TableQrEventPublisher}, mirroring the
 * mock setup in {@link TableQrProvisioningServiceTest}.
 *
 * <p>Only the provisioning pipeline is mocked — provisioning itself is already
 * covered by {@code TableQrProvisioningServiceTest}. Branch/merchant lookup and the
 * table save still run through {@link TableService#createTable} for real, so a test
 * built on this harness exercises {@code createTable}'s own assembly of the response
 * and the published event, not a canned answer.
 */
class TableServiceHarness {

    private static final UUID MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long BRANCH_ID = 5L;
    private static final Long TABLE_ID = 42L;

    private final TableService service;
    private final TableQrEventPublisher publisher;

    private TableServiceHarness(TableService service, TableQrEventPublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    /**
     * Builds a harness where provisioning the table under test returns an ACTIVE
     * {@link TableQrEntity} carrying the given terminal label and payload.
     */
    static TableServiceHarness withProvisionedQr(String terminalLabel, String payload) {
        TableRepository tableRepository = mock(TableRepository.class);
        BranchRepository branchRepository = mock(BranchRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        TableQrProvisioningService provisioningService = mock(TableQrProvisioningService.class);
        TableQrEventPublisher publisher = mock(TableQrEventPublisher.class);

        PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");
        QrSignatureService signatures = new QrSignatureService("master-secret-value", "");

        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(BranchEntity.builder()
                .id(BRANCH_ID).merchantId(MERCHANT_ID).name("Main").slug("main")
                .phone("+251900000000").address("Bole").build()));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(MerchantEntity.builder()
                .id(MERCHANT_ID).name("Sunrise Coffee").slug("sunrise")
                .phone("+251900000000").city("Addis Ababa").address("Bole").category("CAFE").build()));
        // save() assigns the id the database would have assigned, same as TableQrUrlTest.
        when(tableRepository.save(any(TableEntity.class))).thenAnswer(invocation -> {
            TableEntity table = invocation.getArgument(0);
            table.setId(TABLE_ID);
            return table;
        });
        when(provisioningService.provision(any(TableQrProvisioningService.TableRef.class)))
                .thenReturn(TableQrEntity.builder()
                        .id(1L).tableId(TABLE_ID).merchantId(MERCHANT_ID).branchId(BRANCH_ID)
                        .terminalLabel(terminalLabel).payloadRaw(payload)
                        .payloadCrc(payload.length() >= 4 ? payload.substring(payload.length() - 4) : payload)
                        .profile("EMVCO").version(1).state("ACTIVE").provisionedAt(LocalDateTime.now())
                        .build());

        TableService service = new TableService(tableRepository, branchRepository, merchantRepository,
                urls, signatures, provisioningService, publisher);

        return new TableServiceHarness(service, publisher);
    }

    TableService service() {
        return service;
    }

    TableQrEventPublisher publisher() {
        return publisher;
    }
}
