package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.merchant.service.CustomerRequestService;
import com.qrserve.merchant.service.TableAssignmentService;
import com.qrserve.shared.common.dto.ResolveRequestDto;
import com.qrserve.shared.common.dto.WaiterAssignmentDto;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tenant scoping on the table-assignment, customer-request and waiter-task
 * endpoints: every non-SUPER_ADMIN caller is pinned to its own merchant from the
 * token, whatever ids or query params it supplies.
 */
class TenantScopedControllersTest {

    private static final UUID OWN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private TableAssignmentService assignmentService;
    private CustomerRequestService requestService;
    private TableRepository tableRepository;
    private WaiterRepository waiterRepository;

    @BeforeEach
    void setUp() {
        assignmentService = mock(TableAssignmentService.class);
        requestService = mock(CustomerRequestService.class);
        tableRepository = mock(TableRepository.class);
        waiterRepository = mock(WaiterRepository.class);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    private static TableAssignmentEntity assignment(UUID merchantId) {
        return TableAssignmentEntity.builder().id(1L).merchantId(merchantId).tableId(42L).status("ACTIVE").build();
    }

    // --- TableAssignmentV1Controller ---

    @Test
    void v1AssignRefusesAnotherTenantsTable() {
        when(tableRepository.findById(42L)).thenReturn(Optional.of(
                TableEntity.builder().id(42L).merchantId(OTHER).branchId(5L).build()));
        var controller = new TableAssignmentV1Controller(assignmentService, tableRepository, waiterRepository);

        assertThrows(AccessDeniedException.class, () -> controller.assignWaiter(42L,
                WaiterAssignmentDto.builder().waiterId(9L).build(), principal(OWN, UserRole.WAITER)));
        verify(assignmentService, never()).assignWaiterToTable(any(), any(), any(), any(), any());
    }

    // --- TableAssignmentController ---

    @Test
    void assignmentLookupsByIdOnlyReturnTheCallersTenant() {
        when(assignmentService.getAssignmentsForTable(42L)).thenReturn(List.of(assignment(OTHER), assignment(OWN)));
        when(assignmentService.getActiveAssignmentForTable(42L)).thenReturn(assignment(OTHER));
        when(assignmentService.getActiveAssignmentsForWaiter(9L)).thenReturn(List.of(assignment(OTHER)));
        var controller = new TableAssignmentController(assignmentService);
        UserPrincipal owner = principal(OWN, UserRole.MERCHANT_OWNER);

        assertEquals(1, controller.getByTable(42L, owner).getBody().size());
        assertEquals(204, controller.getActiveByTable(42L, owner).getStatusCode().value());
        assertTrue(controller.getActiveByWaiter(9L, owner).getBody().isEmpty());
        assertEquals(2, controller.getByTable(42L, principal(null, UserRole.SUPER_ADMIN)).getBody().size());
    }

    @Test
    void endAssignmentUsesTheTokensTenantNotTheQueryParam() {
        var controller = new TableAssignmentController(assignmentService);

        controller.endAssignment(1L, OTHER, principal(OWN, UserRole.BRANCH_MANAGER));

        verify(assignmentService).endAssignment(1L, OWN);
    }

    // --- CustomerRequestController ---

    @Test
    void branchRequestListIsFilteredToTheCallersTenant() {
        CustomerRequestEntity own = CustomerRequestEntity.builder().id(1L).merchantId(OWN).branchId(5L).build();
        CustomerRequestEntity foreign = CustomerRequestEntity.builder().id(2L).merchantId(OTHER).branchId(5L).build();
        when(requestService.getRequestsByBranch(5L)).thenReturn(List.of(own, foreign));
        var controller = new CustomerRequestController(requestService);

        var rows = controller.getRequests(OTHER, 5L, null, principal(OWN, UserRole.WAITER)).getBody();

        assertEquals(List.of(own), rows);
    }

    // --- WaiterTaskV1Controller ---

    @Test
    void resolveRequestUsesTheTokensTenantNotTheQueryParam() {
        var controller = new WaiterTaskV1Controller(assignmentService, requestService, waiterRepository, tableRepository);

        controller.resolveRequest(7L, ResolveRequestDto.builder().status("COMPLETED").build(),
                OTHER, principal(OWN, UserRole.KITCHEN));

        verify(requestService).updateRequestStatus(7L, "COMPLETED", OWN);
    }

    @Test
    void tasksForAnotherMerchantAreForbidden() {
        var controller = new WaiterTaskV1Controller(assignmentService, requestService, waiterRepository, tableRepository);

        assertThrows(AccessDeniedException.class,
                () -> controller.getTasks(OTHER, 5L, null, null, principal(OWN, UserRole.WAITER)));
        verify(requestService, never()).getPendingRequestsByBranch(eq(5L));
    }
}
