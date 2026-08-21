package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.TableQrRepository;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TerminalLabel;
import com.qrserve.shared.common.emvco.EmvcoMerchant;
import com.qrserve.shared.common.emvco.EmvcoPayload;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mints and stores the static payload for a table.
 *
 * <p>Called inside the table-creation transaction, so a table cannot exist without a
 * scannable code. Reprints supersede rather than replace.
 */
@Service
@RequiredArgsConstructor
public class TableQrProvisioningService {

    /** Restaurants. EMVCo tag 52. */
    private static final String MCC_RESTAURANT = "5812";
    private static final String CURRENCY_ETB = "230";
    private static final String COUNTRY_ET = "ET";
    private static final String GUID = "ET.QRSERVE";
    private static final String PROFILE_EMVCO = "EMVCO";

    /** The fallback profile used when a merchant has no settlement destination on file. */
    private static final String PROFILE_MENU_URL = "MENU_URL";

    private final TableQrRepository repository;
    private final MerchantSettingsService settingsService;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;

    /** Everything the payload needs, passed in so this service reads no other table. */
    public record TableRef(
            Long tableId,
            UUID merchantId,
            Long branchId,
            String merchantSlug,
            String merchantName,
            String merchantCity,
            String branchSlug,
            String tableNumber) {
    }

    @Transactional
    public TableQrEntity provision(TableRef ref) {
        repository.findByTableIdAndState(ref.tableId(), "ACTIVE").ifPresent(active -> {
            throw new IllegalStateException(
                    "Table " + ref.tableId() + " already has an ACTIVE terminal label "
                            + active.getTerminalLabel() + "; use reprint(...) to supersede it");
        });
        return mintNext(ref);
    }

    /**
     * The sticker currently valid for this table — what qr-service renders and
     * nothing else. 404s rather than returning empty, because the caller (an image
     * render, or a printable-code export) has nothing useful to do without it.
     */
    public TableQrEntity getActive(Long tableId) {
        return repository.findByTableIdAndState(tableId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active QR for table ID: " + tableId));
    }

    /** Issues the next version and supersedes whatever is currently ACTIVE. */
    @Transactional
    public TableQrEntity reprint(Long tableId, TableRef ref) {
        repository.findByTableIdAndState(tableId, "ACTIVE").ifPresent(active -> {
            active.setState("SUPERSEDED");
            active.setSupersededAt(LocalDateTime.now());
            repository.save(active);
        });
        return mintNext(ref);
    }

    private TableQrEntity mintNext(TableRef ref) {
        int version = repository.findTopByTableIdOrderByVersionDesc(ref.tableId())
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        return mint(ref, version);
    }

    private TableQrEntity mint(TableRef ref, int version) {
        String terminalLabel = TerminalLabel.of(ref.tableId(), version);
        String destination = settingsService.destinationRef(ref.merchantId(), ref.branchId());

        TableQrEntity.TableQrEntityBuilder qr = TableQrEntity.builder()
                .tableId(ref.tableId())
                .merchantId(ref.merchantId())
                .branchId(ref.branchId())
                .terminalLabel(terminalLabel)
                .version(version)
                .state("ACTIVE");

        if (destination != null && !destination.isBlank()) {
            // The destination is configured: build the real EMVCo payment payload.
            EmvcoMerchant merchant = new EmvcoMerchant(
                    GUID, destination, MCC_RESTAURANT, CURRENCY_ETB, COUNTRY_ET,
                    ref.merchantName(), ref.merchantCity());
            String payload = EmvcoPayload.staticPayload(merchant, terminalLabel, ref.branchSlug());

            qr.payloadRaw(payload)
                    .payloadCrc(payload.substring(payload.length() - 4))
                    .profile(PROFILE_EMVCO);
        } else {
            // No settlement destination on file: fall back to the signed menu URL
            // rather than refusing table creation outright. Built exactly the way
            // TableService builds it today, so the two cannot drift.
            String signature = qrSignatureService.generateSignature(
                    ref.merchantId(), ref.branchId(), ref.tableId());
            String payload = publicMenuUrl.menuUrl(
                    ref.merchantSlug(), ref.branchSlug(), ref.tableNumber(), signature);

            // A menu URL has no CRC; a sliced substring of it would be meaningless
            // data pretending to be a checksum, so this is null rather than a lie.
            qr.payloadRaw(payload)
                    .payloadCrc(null)
                    .profile(PROFILE_MENU_URL);
        }

        return repository.save(qr.build());
    }
}
