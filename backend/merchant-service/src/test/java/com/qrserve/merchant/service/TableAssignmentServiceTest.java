package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.repository.TableAssignmentRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TableAssignmentServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long BRANCH = 5L;

    private TableAssignmentRepository assignmentRepository;
    private WaiterRepository waiterRepository;
    private TableRepository tableRepository;
    private TableAssignmentService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(TableAssignmentRepository.class);
        waiterRepository = mock(WaiterRepository.class);
        tableRepository = mock(TableRepository.class);
        service = new TableAssignmentService(assignmentRepository, waiterRepository, tableRepository);
        when(tableRepository.findById(42L)).thenReturn(Optional.of(
                TableEntity.builder().id(42L).merchantId(MERCHANT).branchId(BRANCH).build()));
    }

    private void waiter(String status) {
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(
                WaiterEntity.builder().id(9L).merchantId(MERCHANT).branchId(BRANCH).status(status).build()));
    }

    @Test
    void anInactiveWaiterCannotBeAssigned() {
        waiter("INACTIVE");

        assertThrows(BusinessException.class, () -> service.assignWaiterToTable(MERCHANT, BRANCH, 42L, 9L, null));
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void losingAConcurrentAssignRaceIsAConflict() {
        waiter("ACTIVE");
        when(assignmentRepository.findByTableIdAndStatus(42L, "ACTIVE")).thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assignWaiterToTable(MERCHANT, BRANCH, 42L, 9L, null));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void reassigningEndsThePreviousActiveAssignmentFirst() {
        waiter("ACTIVE");
        TableAssignmentEntity previous = TableAssignmentEntity.builder()
                .id(1L).merchantId(MERCHANT).tableId(42L).status("ACTIVE").build();
        when(assignmentRepository.findByTableIdAndStatus(42L, "ACTIVE")).thenReturn(Optional.of(previous));
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        TableAssignmentEntity created = service.assignWaiterToTable(MERCHANT, BRANCH, 42L, 9L, "MORNING");

        assertEquals("ENDED", previous.getStatus());
        assertEquals("ACTIVE", created.getStatus());
        var order = inOrder(assignmentRepository);
        order.verify(assignmentRepository).saveAndFlush(previous);
        order.verify(assignmentRepository).saveAndFlush(created);
    }

    @Test
    void anAlreadyEndedAssignmentCannotBeEndedAgain() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(TableAssignmentEntity.builder()
                .id(1L).merchantId(MERCHANT).status("ENDED").build()));

        assertThrows(BusinessException.class, () -> service.endAssignment(1L, MERCHANT));
        verify(assignmentRepository, never()).save(any());
    }
}
