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

        return CreateTableResponse.builder()
                .id(saved.getId())
                .tableNumber(saved.getTableNumber())
                .capacity(saved.getCapacity())
                .qrUrl(qrUrl)
                .qrToken(saved.getQrToken())
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
}