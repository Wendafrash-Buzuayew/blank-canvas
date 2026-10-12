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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
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

    /** EMVCo caps for tags 59 (name), 60 (city) and 62-03 (store label). */
    private static final int MAX_EMVCO_NAME = 25;
    private static final int MAX_EMVCO_CITY = 15;
    private static final int MAX_EMVCO_STORE_LABEL = 25;

    /** Tag 60 is mandatory; used when the merchant's city has no ASCII form. */
    private static final String DEFAULT_EMVCO_CITY = "ADDIS ABABA";

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

    /** True when this table already has an ACTIVE row, so the caller knows to reprint(...) instead of provision(...). */
    public boolean hasActive(Long tableId) {
        return repository.findByTableIdAndState(tableId, "ACTIVE").isPresent();
    }

    /** Issues the next version and supersedes whatever is currently ACTIVE. */
    @Transactional
    public TableQrEntity reprint(Long tableId, TableRef ref) {
        repository.findByTableIdAndState(tableId, "ACTIVE").ifPresent(active -> {
            active.setState("SUPERSEDED");
            active.setSupersededAt(LocalDateTime.now());
            // Flushed now: Hibernate flushes inserts before updates, so the new
            // ACTIVE row would otherwise hit uq_table_qr_active alongside this one.
            repository.saveAndFlush(active);
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
            // EmvcoPayload rejects non-ASCII and over-cap values, so an Amharic or
            // long merchant name used to 400 every createTable. Display fields are
            // sanitised here instead; they are labels, not identifiers.
            EmvcoMerchant merchant = new EmvcoMerchant(
                    GUID, destination, MCC_RESTAURANT, CURRENCY_ETB, COUNTRY_ET,
                    emvcoText(ref.merchantName(), MAX_EMVCO_NAME, emvcoText(ref.merchantSlug(), MAX_EMVCO_NAME, "MERCHANT")),
                    emvcoText(ref.merchantCity(), MAX_EMVCO_CITY, DEFAULT_EMVCO_CITY));
            String payload = EmvcoPayload.staticPayload(
                    merchant, terminalLabel, emvcoText(ref.branchSlug(), MAX_EMVCO_STORE_LABEL, terminalLabel));

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

        // Two concurrent reprints both read the same latest version and both try
        // to insert an ACTIVE row; uq_table_qr_active rejects the loser. That is a
        // conflict for the caller to retry, not a 500.
        try {
            return repository.saveAndFlush(qr.build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Table " + ref.tableId() + " QR was reprinted concurrently. Reload and try again.");
        }
    }

    /**
     * Reduces a display value to what an EMVCo text tag accepts: printable US-ASCII
     * (accents folded, other scripts dropped), at most {@code maxLength} chars.
     * Falls back when nothing survives, e.g. a merchant name written in Ge'ez.
     */
    static String emvcoText(String value, int maxLength, String fallback) {
        String ascii = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("[^\\x20-\\x7E]", "")
                .replaceAll(" +", " ")
                .trim();
        if (ascii.isEmpty()) {
            ascii = fallback;
        }
        return ascii.length() > maxLength ? ascii.substring(0, maxLength).trim() : ascii;
    }
}
