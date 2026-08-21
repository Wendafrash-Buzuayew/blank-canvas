package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This projection is what stands between an external payment and an
 * UNKNOWN_TERMINAL hold, so it has to be durable and it has to tolerate Kafka
 * delivering the same event more than once.
 */
class TerminalMapServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TerminalMapRepository repository;
    private TerminalMapService service;

    private TableQrProvisionedEvent event(String label, long tableId, int version) {
        return TableQrProvisionedEvent.builder()
                .terminalLabel(label).tableId(tableId).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(version).provisionedAt(LocalDateTime.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        repository = mock(TerminalMapRepository.class);
        service = new TerminalMapService(repository);
        when(repository.save(any(TerminalMapEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("a provisioning event becomes a resolvable mapping")
    void recordsMapping() {
        when(repository.findById("T42-1")).thenReturn(Optional.empty());

        service.record(event("T42-1", 42L, 1));

        verify(repository).save(any(TerminalMapEntity.class));
    }

    @Test
    @DisplayName("a redelivered event is not written twice")
    void idempotentOnRedelivery() {
        // Kafka is at-least-once, and the projection's primary key is the label, so a
        // second insert would fail the listener and stall the partition. Safety here
        // rests on merchant-service keying the Kafka send by terminalLabel, so a
        // redelivery of the same label always lands with a matching tableId.
        TerminalMapEntity stored = TerminalMapEntity.builder()
                .terminalLabel("T42-1").tableId(42L).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(1).build();
        when(repository.findById("T42-1")).thenReturn(Optional.of(stored));

        service.record(event("T42-1", 42L, 1));

        verify(repository, never()).save(any(TerminalMapEntity.class));
    }

    @Test
    @DisplayName("a conflicting same-label event is logged and ignored, not written")
    void conflictingTableIdIsIgnored() {
        // A stored row with a different tableId than the incoming event is the one
        // anomaly worth surfacing loudly rather than silently discarding.
        TerminalMapEntity stored = TerminalMapEntity.builder()
                .terminalLabel("T42-1").tableId(42L).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(1).build();
        when(repository.findById("T42-1")).thenReturn(Optional.of(stored));

        service.record(event("T42-1", 99L, 1));

        verify(repository, never()).save(any(TerminalMapEntity.class));
        assertEquals(42L, stored.getTableId(), "the stored row must be left untouched by a conflicting event");
    }

    @Test
    @DisplayName("a superseded label still resolves, because its sticker may still be on the table")
    void supersededLabelStillResolves() {
        TerminalMapEntity v1 = TerminalMapEntity.builder()
                .terminalLabel("T42-1").tableId(42L).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(1).build();
        when(repository.findById("T42-1")).thenReturn(Optional.of(v1));

        Optional<TerminalMapEntity> resolved = service.resolve("T42-1");

        assertTrue(resolved.isPresent(), "a reprint must not orphan payments from old stickers");
        assertEquals(42L, resolved.get().getTableId());
    }

    @Test
    @DisplayName("an unknown label resolves to empty rather than throwing")
    void unknownLabelIsEmpty() {
        when(repository.findById("T99-9")).thenReturn(Optional.empty());
        assertTrue(service.resolve("T99-9").isEmpty());
    }

    @Test
    @DisplayName("a null or blank label resolves to empty")
    void blankLabelIsEmpty() {
        // A webhook that omits tag 62-07 must land as UNKNOWN_TERMINAL, not as an
        // exception that leaves the payment unrecorded.
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve("  ").isEmpty());
    }
}
