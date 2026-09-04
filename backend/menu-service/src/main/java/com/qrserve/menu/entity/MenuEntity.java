package com.qrserve.menu.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One menu per branch (1:1), not a separately-named multi-variant concept —
 * different branches can carry different items/prices, but a branch does not
 * host multiple concurrent named menus. See
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md.
 */
@Entity
@Table(name = "menus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuEntity {

    public enum Status { DRAFT, PUBLISHED }

    /**
     * A small, team-curated, fixed set of visual presentations for the
     * customer-facing digital menu page — a merchant picks one, not a
     * merchant-authored/customizable template system.
     */
    public enum TemplateStyle { CLASSIC, MODERN_DARK, VIBRANT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    // Denormalized from the owning branch's merchant, so tenant checks on
    // Category/Product (which will carry this same denormalized field) do
    // not require a join through Branch (a different service's database).
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    // Nullable + defaulted in prePersist (like status above), not
    // nullable=false: avoids a NOT NULL column landing on an
    // already-populated table under this repo's ddl-auto=update convention.
    @Enumerated(EnumType.STRING)
    @Column(name = "template_style")
    private TemplateStyle templateStyle;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) status = Status.DRAFT;
        if (templateStyle == null) templateStyle = TemplateStyle.CLASSIC;
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
