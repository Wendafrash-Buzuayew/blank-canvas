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
 * <p>EVERY field here is a constrained choice (an enum or a boolean), never a
 * raw hex value, font name, or CSS string - the admin picker only offers a
 * fixed set of DESIGN.md-vetted options, so no combination this entity can
 * hold is capable of rendering illegible text, however many templates exist.
 * That invariant is the reason the structural fields added below are enums
 * rather than, say, a free-form column count or a font name.
 *
 * <p>SCHEMA NOTE: the structural columns are declared nullable on purpose.
 * Flyway is disabled and {@code ddl-auto: update} adds columns to a table
 * that already has rows, where a NOT NULL column without a default fails
 * outright. The hand-run migration in
 * {@code db/manual/001-menu-template-structure.sql} backfills existing rows
 * and then applies NOT NULL. Reads tolerate null regardless - see
 * {@link #normalised()}, which every read path goes through, so a row written
 * before the migration still resolves to a complete, renderable definition.
 *
 * <p>The {@code @Builder.Default} values below do NOT make normalised()
 * redundant. Lombok applies them in the builder and (since 1.18.2) in the
 * no-arg constructor, but Hibernate constructs through that no-arg
 * constructor and THEN populates each field from the ResultSet - so a NULL
 * column overwrites the default a moment after it was set. The defaults
 * protect objects this code creates; normalised() protects objects the
 * database returns.
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

    /**
     * How item cards are arranged within a category.
     *
     * <p>GRID_2 and GRID_3 are separate values rather than one GRID plus a
     * column count, because a column count is only meaningful for a grid: a
     * {@code gridColumns} field would be dead data on the other three layouts
     * and would let an admin save "SINGLE_COLUMN with 3 columns". Every value
     * of this enum is a complete, valid layout on its own.
     *
     * <p>All five collapse to a single column below the {@code sm} breakpoint -
     * a two-up grid of dishes on a 375px phone is unreadable, and the customer
     * context is phone-first (DESIGN.md §1).
     */
    public enum LayoutStructure { GRID_2, GRID_3, SINGLE_COLUMN, TWO_COLUMN, COMPACT_LIST }

    /** How an individual item card is delineated from the page ground. */
    public enum ItemCardStyle { CARD_BORDERED, CARD_FLAT, ELEVATED_SHADOW, MINIMAL_DIVIDER }

    /**
     * Where the dish image sits relative to its text.
     *
     * <p>NONE overlaps with {@code showImages = false}. Both are kept because
     * they are edited independently in the admin form - the toggle is the
     * quick "off", the position is the layout choice - but they resolve
     * through ONE predicate, {@link #imagesVisible()}, so the two can never
     * disagree at render time.
     */
    public enum ImagePosition { TOP, LEFT, RIGHT, NONE }

    /** The aspect box a dish image is cropped into. */
    public enum ImageAspectRatio { SQUARE_1_1, LANDSCAPE_16_9, ROUNDED_AVATAR }

    /**
     * Display face for the menu's headings and item names.
     *
     * <p>SERIF and MODERN_MONO resolve to system stacks, not to licensed
     * webfonts - the product ships Proxima Nova only (DESIGN.md §4.1). The
     * customer digital menu is the one context DESIGN.md §1 rule 3 permits to
     * be expressive, which is why a per-template face is allowed here and
     * nowhere else in the product.
     */
    public enum FontFamily { SANS_SERIF, SERIF, MODERN_MONO }

    /** Alignment of the menu header block (cover, title, rating). */
    public enum HeaderAlignment { LEFT, CENTER }

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

    // ---- Structure -------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_structure", length = 20)
    @Builder.Default
    private LayoutStructure layoutStructure = LayoutStructure.SINGLE_COLUMN;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_card_style", length = 20)
    @Builder.Default
    private ItemCardStyle itemCardStyle = ItemCardStyle.CARD_BORDERED;

    // ---- Imagery ---------------------------------------------------------

    @Column(name = "show_images")
    @Builder.Default
    private Boolean showImages = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_position", length = 20)
    @Builder.Default
    private ImagePosition imagePosition = ImagePosition.LEFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_aspect_ratio", length = 20)
    @Builder.Default
    private ImageAspectRatio imageAspectRatio = ImageAspectRatio.SQUARE_1_1;

    // ---- Typography & header --------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "font_family", length = 20)
    @Builder.Default
    private FontFamily fontFamily = FontFamily.SANS_SERIF;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_alignment", length = 20)
    @Builder.Default
    private HeaderAlignment headerAlignment = HeaderAlignment.CENTER;

    @Column(name = "show_cover_image")
    @Builder.Default
    private Boolean showCoverImage = Boolean.TRUE;

    // ---- Null tolerance --------------------------------------------------

    /**
     * The single predicate for "does this template render dish images?".
     *
     * <p>Two independent fields can express the same "off" state, so callers
     * must never test either one alone: {@code showImages = true} with
     * {@code imagePosition = NONE} means no images, and so does
     * {@code showImages = false} with {@code imagePosition = LEFT}.
     */
    public boolean imagesVisible() {
        return !Boolean.FALSE.equals(showImages) && imagePosition != ImagePosition.NONE;
    }

    /**
     * Fills in any structural field left null by a row that predates the
     * structure migration, so a caller never has to null-check them.
     *
     * <p>Mutates and returns {@code this}: these are the values the row is
     * defined to have, and persisting them on the next write is correct
     * rather than a side effect to avoid.
     */
    public MenuTemplateEntity normalised() {
        if (layoutStructure == null) layoutStructure = LayoutStructure.SINGLE_COLUMN;
        if (itemCardStyle == null) itemCardStyle = ItemCardStyle.CARD_BORDERED;
        if (showImages == null) showImages = Boolean.TRUE;
        if (imagePosition == null) imagePosition = ImagePosition.LEFT;
        if (imageAspectRatio == null) imageAspectRatio = ImageAspectRatio.SQUARE_1_1;
        if (fontFamily == null) fontFamily = FontFamily.SANS_SERIF;
        if (headerAlignment == null) headerAlignment = HeaderAlignment.CENTER;
        if (showCoverImage == null) showCoverImage = Boolean.TRUE;
        return this;
    }
}
