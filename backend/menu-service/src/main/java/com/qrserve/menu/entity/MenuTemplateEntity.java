package com.qrserve.menu.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The visual DEFINITION of a curated template - what {@link MenuEntity.TemplateStyle}
 * on a menu merely references by name. SUPER_ADMIN edits these rows (see
 * MenuTemplateController); a merchant only ever picks one of them by key
 * (MenuService.setTemplate) - they never define new colors themselves.
 *
 * <p>{@code key} is intentionally the same string as a {@link MenuEntity.TemplateStyle}
 * enum constant (CLASSIC / MODERN_DARK / VIBRANT), not a separate generated id: there
 * are exactly three fixed slots today, matching that enum, and this is deliberately
 * not open-ended template creation.
 *
 * <p>Every field here is a constrained choice (an enum), never a raw hex value -
 * the frontend picker only offers a fixed set of DESIGN.md-vetted options, so no
 * combination this entity can hold is capable of rendering illegible text.
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
