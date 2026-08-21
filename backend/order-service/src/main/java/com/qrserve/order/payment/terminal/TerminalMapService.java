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

    /** Idempotent: Kafka is at-least-once and the label is the primary key. */
    @Transactional
    public void record(TableQrProvisionedEvent event) {
        if (repository.existsById(event.getTerminalLabel())) {
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
