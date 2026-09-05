package com.qrserve.menu.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The visual DEFINITION of a curated template - what {@code MenuEntity.templateStyle}
 * on a menu merely references by key. SUPER_ADMIN creates, edits, and deletes
 * these rows (see MenuTemplateController); a merchant only ever picks one of
 * them by key (MenuService.setTemplate) - they never define new colors
 * themselves.
 *
 * <p>{@code key} is a short, admin-chosen identifier (e.g. CLASSIC, HORECA) -
 * unique, immutable once created, and validated against
 * {@code MenuEntity.templateStyle} by MenuService.setTemplate before a
 * branch can be pointed at it.
 *
 * <p>backgroundMode/accentToken are each a constrained choice (an enum),
 * never a raw hex value - the admin picker only offers a fixed set of
 * DESIGN.md-vetted options, so no combination this entity can hold is
 * capable of rendering illegible text, however many templates exist.
 */
@Entity
@Table(name = "menu_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuTemplateEntity {

    /**
     * Which ground the customer menu page renders on for this template, and
     * therefore which token pair is safe for its text - DIGITAL_MENU rendering
     * picks ink/muted for LIGHT and TINTED, on-ink/on-ink-muted for DARK. Never
     * a free color: this is what keeps every combination provably readable.
     */
    public enum BackgroundMode { LIGHT, TINTED, DARK }

    /**
     * Which DESIGN.md-sanctioned semantic family drives the category-heading
     * and price color. Applied as safe TEXT (brand-press/info/warn/danger) on
     * a LIGHT/TINTED background, or as a small filled chip (bg-*-fill with ink
     * text) on a DARK background - never as raw text on a dark ground, since
     * none of these families has a measured dark-ground text pairing.
     */
    public enum AccentToken { BRAND, INFO, WARN, DANGER }

    @Id
    @Column(length = 20)
    private String key;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_mode", nullable = false, length = 20)
    private BackgroundMode backgroundMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "accent_token", nullable = false, length = 20)
    private AccentToken accentToken;
}
