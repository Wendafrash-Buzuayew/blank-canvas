package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalMapService {

    private final TerminalMapRepository repository;

    /**
     * Idempotent: Kafka is at-least-once and the label is the primary key.
     *
     * <p>The read-then-write here is a time-of-check/time-of-use race in general —
     * two concurrent consumers could both see no row and both attempt to save. It is
     * safe only because {@code TableQrEventPublisher} in merchant-service keys the
     * {@code table-qr-provisioned} Kafka send by {@code terminalLabel}, so every event
     * for one label lands on one partition and is handled by one consumer instance at
     * a time. If that producer ever stops keying by terminal label, this guard must be
     * replaced with a database-level uniqueness check (e.g. catching the constraint
     * violation from a blind insert) instead of relying on partition ordering.
     */
    @Transactional
    public void record(TableQrProvisionedEvent event) {
        Optional<TerminalMapEntity> existing = repository.findById(event.getTerminalLabel());
        if (existing.isPresent()) {
            if (!existing.get().getTableId().equals(event.getTableId())) {
                log.warn("Terminal label {} already maps to table {} but event claims table {}; ignoring, row left unchanged",
                        event.getTerminalLabel(), existing.get().getTableId(), event.getTableId());
            }
            return;
        }
        repository.save(TerminalMapEntity.builder()
                .terminalLabel(event.getTerminalLabel())
                .tableId(event.getTableId())
                .merchantId(event.getMerchantId())
                .branchId(event.getBranchId())
                .tableNumber(event.getTableNumber())
                .version(event.getVersion())
                .build());
    }

    /**
     * Resolves any label ever issued, superseded ones included.
     *
     * @return empty for an unknown, null or blank label — the caller turns that into
     *         an UNKNOWN_TERMINAL hold, which is a recorded payment awaiting staff,
     *         never a thrown exception that loses it
     */
    @Transactional(readOnly = true)
    public Optional<TerminalMapEntity> resolve(String terminalLabel) {
        if (terminalLabel == null || terminalLabel.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(terminalLabel);
    }
}
