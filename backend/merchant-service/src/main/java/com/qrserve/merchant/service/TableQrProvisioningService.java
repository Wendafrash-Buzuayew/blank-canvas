package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.TableQrRepository;
import com.qrserve.shared.common.TerminalLabel;
import com.qrserve.shared.common.emvco.EmvcoMerchant;
import com.qrserve.shared.common.emvco.EmvcoPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
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

    private final TableQrRepository repository;
    private final MerchantSettingsService settingsService;

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
        int version = repository.findTopByTableIdOrderByVersionDesc(ref.tableId())
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        return mint(ref, version);
    }

    /** Issues the next version and supersedes whatever is currently ACTIVE. */
    @Transactional
    public TableQrEntity reprint(Long tableId, TableRef ref) {
        repository.findByTableIdAndState(tableId, "ACTIVE").ifPresent(active -> {
            active.setState("SUPERSEDED");
            active.setSupersededAt(LocalDateTime.now());
            repository.save(active);
        });
        return provision(ref);
    }

    private TableQrEntity mint(TableRef ref, int version) {
        ResolvedMerchantSettings settings = settingsService.resolve(ref.merchantId(), ref.branchId());
        String destination = destinationOf(ref);

        String terminalLabel = TerminalLabel.of(ref.tableId(), version);
        EmvcoMerchant merchant = new EmvcoMerchant(
                GUID, destination, MCC_RESTAURANT, CURRENCY_ETB, COUNTRY_ET,
                ref.merchantName(), ref.merchantCity());

        String payload = EmvcoPayload.staticPayload(merchant, terminalLabel, ref.branchSlug());

        return repository.save(TableQrEntity.builder()
                .tableId(ref.tableId())
                .merchantId(ref.merchantId())
                .branchId(ref.branchId())
                .terminalLabel(terminalLabel)
                .payloadRaw(payload)
                .payloadCrc(payload.substring(payload.length() - 4))
                .profile(PROFILE_EMVCO)
                .version(version)
                .state("ACTIVE")
                .build());
    }

    /**
     * No destination, no code. A payload built with a guessed account sends a guest's
     * money somewhere nobody chose, and a printed sticker cannot be recalled — so an
     * unconfigured merchant fails here, loudly, before anything is laminated.
     */
    private String destinationOf(TableRef ref) {
        return Optional.ofNullable(
                        settingsService.destinationRef(ref.merchantId(), ref.branchId()))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + ref.merchantId() + " has no settlement destination; "
                                + "configure MerchantSettings before provisioning table QRs"));
    }
}
