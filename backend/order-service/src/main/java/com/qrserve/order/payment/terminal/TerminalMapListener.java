package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TerminalMapListener {

    private final TerminalMapService terminalMapService;

    @KafkaListener(topics = "table-qr-provisioned", groupId = "order-service")
    public void onProvisioned(TableQrProvisionedEvent event) {
        log.info("Terminal {} maps to table {}", event.getTerminalLabel(), event.getTableId());
        terminalMapService.record(event);
    }
}
