package com.qrserve.auth.service;

import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the privilege-escalation fix on {@code POST /api/auth/users}.
 *
 * <p>Before this rule existed, {@code CreateUserRequest.role} was caller-supplied
 * with no comparison against the caller's own role, so any MERCHANT_OWNER could
 * create a SUPER_ADMIN.
 */
class AuthServiceRoleHierarchyTest {

    @Test
    @DisplayName("a merchant owner cannot create a super admin")
    void merchantOwnerCannotEscalateToSuperAdmin() {
        assertFalse(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.SUPER_ADMIN));
    }

    @Test
    @DisplayName("a caller cannot create a peer at their own level")
    void callerCannotCreatePeer() {
        assertFalse(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.MERCHANT_OWNER));
        assertFalse(AuthService.canAssignRole(UserRole.BRANCH_MANAGER, UserRole.BRANCH_MANAGER));
        // WAITER, KITCHEN and CASHIER share a rank, so they are peers of each other.
        assertFalse(AuthService.canAssignRole(UserRole.WAITER, UserRole.KITCHEN));
    }

    @Test
    @DisplayName("a caller can create strictly subordinate roles")
    void callerCanCreateSubordinates() {
        assertTrue(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.BRANCH_MANAGER));
        assertTrue(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.WAITER));
        assertTrue(AuthService.canAssignRole(UserRole.BRANCH_MANAGER, UserRole.KITCHEN));
        assertTrue(AuthService.canAssignRole(UserRole.WAITER, UserRole.CUSTOMER));
    }

    @Test
    @DisplayName("a super admin can create every role")
    void superAdminCanCreateAnything() {
        for (UserRole target : UserRole.values()) {
            assertTrue(AuthService.canAssignRole(UserRole.SUPER_ADMIN, target),
                    "SUPER_ADMIN should be able to create " + target);
        }
    }

    @Test
    @DisplayName("a lower role cannot create a higher one")
    void lowerCannotCreateHigher() {
        assertFalse(AuthService.canAssignRole(UserRole.BRANCH_MANAGER, UserRole.MERCHANT_OWNER));
        assertFalse(AuthService.canAssignRole(UserRole.WAITER, UserRole.BRANCH_MANAGER));
        assertFalse(AuthService.canAssignRole(UserRole.CUSTOMER, UserRole.WAITER));
    }

    @Test
    @DisplayName("null roles are rejected rather than defaulting to permitted")
    void nullsAreRejected() {
        assertFalse(AuthService.canAssignRole(null, UserRole.WAITER));
        assertFalse(AuthService.canAssignRole(UserRole.SUPER_ADMIN, null));
    }
}
