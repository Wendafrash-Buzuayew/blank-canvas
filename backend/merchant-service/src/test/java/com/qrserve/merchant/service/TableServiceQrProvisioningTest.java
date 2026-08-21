package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/**
 * A table without a scannable code is a table nobody can order from, so provisioning
 * belongs in the creation transaction rather than in a later screen.
 */
class TableServiceQrProvisioningTest {

    @Test
    @DisplayName("creating a table returns its payload and terminal label")
    void creationReturnsPayload() {
        TableServiceHarness harness = TableServiceHarness.withProvisionedQr("T42-1", "0002010102...9A4D");

        CreateTableResponse response = harness.service().createTable(
                CreateTableRequest.builder().branchId(5L).tableNumber("15").capacity(4).build());

        assertEquals("T42-1", response.getTerminalLabel());
        assertNotNull(response.getQrPayload(), "the caller needs the payload to render a sticker");
    }

    @Test
    @DisplayName("creating a table publishes the terminal mapping")
    void creationPublishesMapping() {
        TableServiceHarness harness = TableServiceHarness.withProvisionedQr("T42-1", "0002010102...9A4D");

        harness.service().createTable(
                CreateTableRequest.builder().branchId(5L).tableNumber("15").capacity(4).build());

        ArgumentCaptor<TableQrProvisionedEvent> captor =
                ArgumentCaptor.forClass(TableQrProvisionedEvent.class);
        verify(harness.publisher()).publish(captor.capture());

        // order-service cannot resolve a webhook's terminal label without this event.
        assertEquals("T42-1", captor.getValue().getTerminalLabel());
        assertEquals(42L, captor.getValue().getTableId());
    }
}
