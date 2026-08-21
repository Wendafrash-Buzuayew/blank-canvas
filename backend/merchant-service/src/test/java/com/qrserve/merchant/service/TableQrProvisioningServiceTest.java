package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.TableQrRepository;
import com.qrserve.shared.common.emvco.Emvco;
import com.qrserve.shared.common.emvco.EmvcoPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provisioning writes the bytes that end up laminated onto a table. Once printed
 * they cannot be corrected, so what is stored has to be exactly what was rendered.
 */
class TableQrProvisioningServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TableQrRepository repository;
    private MerchantSettingsService settings;
    private TableQrProvisioningService service;

    private TableQrProvisioningService.TableRef ref() {
        return new TableQrProvisioningService.TableRef(
                42L, MERCHANT, 5L, "sunrise", "SUNRISE", "ADDIS ABABA", "wello-sefer", "15");
    }

    @BeforeEach
    void setUp() {
        repository = mock(TableQrRepository.class);
        settings = mock(MerchantSettingsService.class);
        service = new TableQrProvisioningService(repository, settings);

        when(repository.save(any(TableQrEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findTopByTableIdOrderByVersionDesc(42L)).thenReturn(Optional.empty());
        when(settings.destinationRef(MERCHANT, 5L)).thenReturn("1234567890");
    }

    @Test
    @DisplayName("first provisioning is version 1 and ACTIVE")
    void firstProvisioning() {
        TableQrEntity qr = service.provision(ref());

        assertEquals(1, qr.getVersion());
        assertEquals("ACTIVE", qr.getState());
        assertEquals("T42-1", qr.getTerminalLabel());
    }

    @Test
    @DisplayName("the stored payload validates its own CRC and carries the terminal label")
    void storedPayloadIsValid() {
        TableQrEntity qr = service.provision(ref());

        assertTrue(EmvcoPayload.crcValid(qr.getPayloadRaw()),
                "a payload whose CRC does not match is rejected by every bank app");
        assertEquals(qr.getPayloadCrc(), qr.getPayloadRaw().substring(qr.getPayloadRaw().length() - 4));

        Map<String, String> tags = Emvco.parseTags(qr.getPayloadRaw());
        assertEquals("11", tags.get("01"), "a table sticker is static");
        assertTrue(tags.get("62").contains("T42-1"), "tag 62 must carry the terminal label");
    }

    @Test
    @DisplayName("a reprint supersedes the old row and issues a new label")
    void reprintSupersedes() {
        TableQrEntity existing = TableQrEntity.builder()
                .id(900L).tableId(42L).merchantId(MERCHANT).branchId(5L)
                .terminalLabel("T42-1").version(1).state("ACTIVE")
                .payloadRaw("irrelevant").payloadCrc("0000").profile("EMVCO")
                .build();
        when(repository.findTopByTableIdOrderByVersionDesc(42L)).thenReturn(Optional.of(existing));
        when(repository.findByTableIdAndState(42L, "ACTIVE")).thenReturn(Optional.of(existing));

        TableQrEntity reprinted = service.reprint(42L, ref());

        assertEquals(2, reprinted.getVersion());
        assertEquals("T42-2", reprinted.getTerminalLabel());
        assertNotEquals(existing.getTerminalLabel(), reprinted.getTerminalLabel());
        // The old sticker may still be on the table, so its row survives and stays
        // resolvable — deleting it would turn real payments into UNKNOWN_TERMINAL.
        assertEquals("SUPERSEDED", existing.getState());
    }

    @Test
    @DisplayName("provisioning a table that already has an ACTIVE row is refused, not duplicated")
    void provisionRefusesWhenAlreadyActive() {
        // uq_table_qr_active allows only one ACTIVE row per table. provision() must
        // refuse before hitting that constraint, because the next developer wiring a
        // "regenerate QR" button will reach for provision() and needs a clear pointer
        // to reprint(...) instead of a raw integrity-violation 500.
        TableQrEntity existing = TableQrEntity.builder()
                .id(900L).tableId(42L).merchantId(MERCHANT).branchId(5L)
                .terminalLabel("T42-1").version(1).state("ACTIVE")
                .payloadRaw("irrelevant").payloadCrc("0000").profile("EMVCO")
                .build();
        when(repository.findByTableIdAndState(42L, "ACTIVE")).thenReturn(Optional.of(existing));

        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.provision(ref()),
                "provision() must not create a second ACTIVE row for the same table");

        assertTrue(ex.getMessage().contains("T42-1"),
                "the refusal must name the existing terminal label so the caller can find the row");
    }

    @Test
    @DisplayName("the destination account comes from settings, never from a default")
    void destinationComesFromSettings() {
        // A payload built with the wrong destination sends a guest's money to the
        // wrong account, so an unconfigured merchant must fail loudly at provisioning
        // rather than print a code that pays somebody else. Branch 9 is deliberately
        // left unstubbed on `settings` (setUp only configures branch 5), so
        // destinationRef(MERCHANT, 9L) returns Mockito's default null and this table
        // never had a chance to inherit the wrong account.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.provision(new TableQrProvisioningService.TableRef(
                        43L, MERCHANT, 9L, "sunrise", "SUNRISE", "ADDIS ABABA", "wello-sefer", "16")),
                "provisioning without a settlement destination must be refused");
    }
}
