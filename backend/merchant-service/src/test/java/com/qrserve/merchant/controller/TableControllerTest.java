package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.TableQrResponse;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.service.TableQrProvisioningService;
import com.qrserve.merchant.service.TableService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The payload behind {@code GET .../qr} embeds the merchant's own settlement
 * account (bank or wallet). Role gates alone let any MERCHANT_OWNER or
 * BRANCH_MANAGER walk table ids and read another merchant's sticker, so the
 * endpoint must also pin the caller to its own tenant, the same way
 * {@link TableController#getAllTables} already does.
 */
class TableControllerTest {

    private static final Long TABLE_ID = 42L;
    private static final UUID OWN_MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private TableService tableService;
    private TableQrProvisioningService provisioningService;
    private TableController controller;

    @BeforeEach
    void setUp() {
        tableService = mock(TableService.class);
        provisioningService = mock(TableQrProvisioningService.class);
        controller = new TableController(tableService, provisioningService);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    private static TableEntity tableOwnedBy(UUID merchantId) {
        return TableEntity.builder()
                .id(TABLE_ID).merchantId(merchantId).branchId(5L)
                .tableNumber("15").capacity(4).status("AVAILABLE").build();
    }

    private static TableQrEntity activeQr(String terminalLabel, int version) {
        return TableQrEntity.builder()
                .id(1L).tableId(TABLE_ID).merchantId(OWN_MERCHANT).branchId(5L)
                .terminalLabel(terminalLabel).payloadRaw("0002010102...9A4D").payloadCrc("9A4D")
                .profile("EMVCO").version(version).state("ACTIVE").build();
    }

    @Test
    @DisplayName("getTableQr returns the payload when the caller owns the table's tenant")
    void getTableQrWithinTenant() {
        when(tableService.getTable(TABLE_ID)).thenReturn(tableOwnedBy(OWN_MERCHANT));
        when(provisioningService.getActive(TABLE_ID)).thenReturn(activeQr("T42-1", 1));

        TableQrResponse body = controller
                .getTableQr(TABLE_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER))
                .getBody();

        assertEquals("T42-1", body.getTerminalLabel());
    }

    @Test
    @DisplayName("getTableQr refuses a caller from a different tenant, without ever reading the QR")
    void getTableQrCrossTenantRefused() {
        when(tableService.getTable(TABLE_ID)).thenReturn(tableOwnedBy(OTHER_MERCHANT));

        assertThrows(UnauthorizedException.class, () ->
                controller.getTableQr(TABLE_ID, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));

        verify(provisioningService, never()).getActive(any());
    }

    @Test
    @DisplayName("getTableQr allows SUPER_ADMIN to read across tenants")
    void getTableQrSuperAdminCrossesTenants() {
        when(tableService.getTable(TABLE_ID)).thenReturn(tableOwnedBy(OTHER_MERCHANT));
        when(provisioningService.getActive(TABLE_ID)).thenReturn(activeQr("T42-1", 1));

        TableQrResponse body = controller
                .getTableQr(TABLE_ID, principal(OWN_MERCHANT, UserRole.SUPER_ADMIN))
                .getBody();

        assertEquals("T42-1", body.getTerminalLabel());
    }
}
