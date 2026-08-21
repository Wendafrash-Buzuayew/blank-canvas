package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.events.TableQrProvisionedEvent;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final BranchRepository branchRepository;
    private final MerchantRepository merchantRepository;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;
    private final TableQrProvisioningService tableQrProvisioningService;
    private final TableQrEventPublisher tableQrEventPublisher;

    @Transactional
    public CreateTableResponse createTable(CreateTableRequest request) {
        BranchEntity branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found ID: " + request.getBranchId()));

        MerchantEntity merchant = merchantRepository.findById(branch.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found ID: " + branch.getMerchantId()));

        String qrToken = "qr-" + merchant.getSlug() + "-" + branch.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);

        TableEntity table = TableEntity.builder()
                .branchId(branch.getId())
                .merchantId(merchant.getId())
                .tableNumber(request.getTableNumber())
                .capacity(request.getCapacity())
                .status("AVAILABLE")
                .qrToken(qrToken)
                .build();

        TableEntity saved = tableRepository.save(table);

        // The URL is built by PublicMenuUrl and nowhere else. The previous format,
        //   https://qrserve.com/menu/{merchantSlug}/{branchId}/{tableId}
        // was wrong three ways at once: the host was hardcoded, the branch was
        // identified by id where PublicMenuResolutionService resolves it by slug,
        // and the table by id where the resolver uses table_number. Every code ever
        // generated resolved to a 404.
        //
        // The signature is emitted here for the first time. It was validated in two
        // places but generated in none, which made the tamper check dead code and
        // left the public service-call endpoint reachable by anyone who could guess
        // a table id.
        String signature = qrSignatureService.generateSignature(
                merchant.getId(), branch.getId(), saved.getId());
        String qrUrl = publicMenuUrl.menuUrl(
                merchant.getSlug(), branch.getSlug(), saved.getTableNumber(), signature);

        // A table without a scannable EMVCo code is a table nobody can pay at, so
        // provisioning happens inside this same transaction rather than on a later
        // screen. This is a fresh table row, so it never has an ACTIVE terminal
        // label yet and provision() cannot hit its own-active-row guard.
        TableQrEntity qr = tableQrProvisioningService.provision(buildTableRef(saved, branch, merchant));

        tableQrEventPublisher.publish(buildProvisionedEvent(qr, saved.getId(), merchant.getId(), branch.getId(), saved.getTableNumber()));

        return CreateTableResponse.builder()
                .id(saved.getId())
                .tableNumber(saved.getTableNumber())
                .capacity(saved.getCapacity())
                .qrUrl(qrUrl)
                .qrToken(saved.getQrToken())
                .terminalLabel(qr.getTerminalLabel())
                .qrPayload(qr.getPayloadRaw())
                .build();
    }

    public TableEntity getTable(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + id));
    }

    /**
     * Lists tables, scoped to a merchant when one is supplied.
     *
     * <p>A null merchantId returns every table and is reserved for SUPER_ADMIN —
     * the controller is responsible for pinning other roles to their own tenant.
     * Filtering happens in the query rather than in the caller so another tenant's
     * rows never leave the database.
     */
    public java.util.List<TableEntity> getAllTables(UUID merchantId) {
        return merchantId != null
                ? tableRepository.findByMerchantId(merchantId)
                : tableRepository.findAll();
    }

    @Transactional
    public TableEntity updateTableStatus(Long id, String status) {
        TableEntity table = getTable(id);
        table.setStatus(status.toUpperCase());
        return tableRepository.save(table);
    }

    /**
     * Provisions a QR for a table that has none yet, or reprints when one is
     * already ACTIVE.
     *
     * <p>Every table created before this branch shipped has no {@code TableQr}
     * row at all — {@code provision()} was previously only ever called from
     * {@link #createTable}. This is the endpoint-facing way to backfill those
     * tables one at a time, and the first production caller of {@code reprint()}.
     */
    @Transactional
    public TableQrEntity provisionOrReprintQr(Long tableId) {
        TableEntity table = getTable(tableId);
        BranchEntity branch = branchRepository.findById(table.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found ID: " + table.getBranchId()));
        MerchantEntity merchant = merchantRepository.findById(table.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found ID: " + table.getMerchantId()));

        TableQrProvisioningService.TableRef ref = buildTableRef(table, branch, merchant);
        TableQrEntity qr = tableQrProvisioningService.hasActive(tableId)
                ? tableQrProvisioningService.reprint(tableId, ref)
                : tableQrProvisioningService.provision(ref);

        tableQrEventPublisher.publish(
                buildProvisionedEvent(qr, table.getId(), merchant.getId(), branch.getId(), table.getTableNumber()));

        return qr;
    }

    /**
     * The one place that assembles a {@link TableQrProvisioningService.TableRef}.
     * {@code createTable} and {@link #provisionOrReprintQr} both need exactly the
     * same fields; duplicating the lookups inline invites the two paths to drift.
     */
    private TableQrProvisioningService.TableRef buildTableRef(
            TableEntity table, BranchEntity branch, MerchantEntity merchant) {
        return new TableQrProvisioningService.TableRef(
                table.getId(), merchant.getId(), branch.getId(),
                merchant.getSlug(), merchant.getName(), merchant.getCity(),
                branch.getSlug(), table.getTableNumber());
    }

    private TableQrProvisionedEvent buildProvisionedEvent(
            TableQrEntity qr, Long tableId, UUID merchantId, Long branchId, String tableNumber) {
        return TableQrProvisionedEvent.builder()
                .terminalLabel(qr.getTerminalLabel())
                .tableId(tableId)
                .merchantId(merchantId)
                .branchId(branchId)
                .tableNumber(tableNumber)
                .version(qr.getVersion())
                .provisionedAt(qr.getProvisionedAt())
                .build();
    }
}