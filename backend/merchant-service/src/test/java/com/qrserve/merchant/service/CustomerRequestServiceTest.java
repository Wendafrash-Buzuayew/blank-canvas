package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.CustomerRequestRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TenantContext;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerRequestServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Long BRANCH = 5L;
    private static final Long TABLE = 42L;

    private CustomerRequestRepository requestRepository;
    private TableRepository tableRepository;
    private QrSignatureService signatures;
    private CustomerRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(CustomerRequestRepository.class);
        tableRepository = mock(TableRepository.class);
        signatures = new QrSignatureService("master-secret-value", "");
        service = new CustomerRequestService(requestRepository, tableRepository, signatures);
        when(tableRepository.findById(TABLE)).thenReturn(Optional.of(TableEntity.builder()
                .id(TABLE).merchantId(MERCHANT).branchId(BRANCH).tableNumber("15").build()));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private String validSignature() {
        return signatures.generateSignature(MERCHANT, BRANCH, TABLE);
    }

    @Test
    void guestRequestTakesTheTenantFromTheSignedTable() {
        CustomerRequestEntity saved = service.createGuestRequest(TABLE, validSignature(), "call_waiter", "hi");

        assertEquals(MERCHANT, saved.getMerchantId());
        assertEquals(BRANCH, saved.getBranchId());
        assertEquals(TABLE, saved.getTableId());
        assertEquals("CALL_WAITER", saved.getRequestType());
        assertEquals("PENDING", saved.getStatus());
    }

    @Test
    void guestRequestWithoutASignatureIsRefused() {
        assertThrows(AccessDeniedException.class, () -> service.createGuestRequest(TABLE, null, "CALL_WAITER", null));
        assertThrows(AccessDeniedException.class, () -> service.createGuestRequest(TABLE, "", "CALL_WAITER", null));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void guestRequestWithAnotherTablesSignatureIsRefused() {
        String forOtherTable = signatures.generateSignature(MERCHANT, BRANCH, 43L);

        assertThrows(AccessDeniedException.class,
                () -> service.createGuestRequest(TABLE, forOtherTable, "CALL_WAITER", null));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void guestRequestFromAnotherTenantsHostIsRefused() {
        TenantContext.setCurrentTenant(OTHER_MERCHANT);

        assertThrows(AccessDeniedException.class,
                () -> service.createGuestRequest(TABLE, validSignature(), "CALL_WAITER", null));
    }

    @Test
    void tableHistoryRequiresTheSignature() {
        assertThrows(AccessDeniedException.class, () -> service.getRequestsByTable(TABLE, null));
        verify(requestRepository, never()).findByTableId(any());

        when(requestRepository.findByTableId(TABLE)).thenReturn(List.of(new CustomerRequestEntity()));
        assertEquals(1, service.getRequestsByTable(TABLE, validSignature()).size());
    }

    private CustomerRequestEntity existing(String status) {
        CustomerRequestEntity r = CustomerRequestEntity.builder()
                .id(7L).merchantId(MERCHANT).branchId(BRANCH).tableId(TABLE).status(status).build();
        when(requestRepository.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void updateWithoutATenantIsForbidden() {
        existing("PENDING");

        assertThrows(AccessDeniedException.class, () -> service.updateRequestStatus(7L, "COMPLETED", null));
    }

    @Test
    void updateFromAnotherTenantLooksNotFound() {
        existing("PENDING");

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateRequestStatus(7L, "COMPLETED", OTHER_MERCHANT));
    }

    @Test
    void pendingCanBeAcknowledgedThenCompleted() {
        CustomerRequestEntity r = existing("PENDING");

        service.updateRequestStatus(7L, "acknowledged", MERCHANT);
        assertEquals("ACKNOWLEDGED", r.getStatus());
        assertNull(r.getResolvedAt());

        service.updateRequestStatus(7L, "COMPLETED", MERCHANT);
        assertEquals("COMPLETED", r.getStatus());
        assertNotNull(r.getResolvedAt());
    }

    @Test
    void aTerminalRequestCannotBeReopened() {
        existing("COMPLETED");

        assertThrows(BusinessException.class, () -> service.updateRequestStatus(7L, "PENDING", MERCHANT));
        assertThrows(BusinessException.class, () -> service.updateRequestStatus(7L, "ACKNOWLEDGED", MERCHANT));
        verify(requestRepository, never()).save(any());
    }
}
