package com.qrserve.backoffice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An append-only record of a significant platform action, written by any
 * service via POST /api/audit-logs. actorUserId/actorRole describe who
 * really performed the action (passed through by the calling service,
 * which derived it from its own caller's principal) — not necessarily the
 * identity the HTTP call itself authenticated as, since a real user's
 * action performed in another service arrives here via that service's own
 * internal service-to-service credential, not the original user's token.
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    /** Null for a platform-wide action with no single owning tenant. */
    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
