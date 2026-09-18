-- Adds the structural, imagery and typography columns to menu_templates.
--
-- WHY THIS IS HAND-RUN: Flyway is disabled across the backend
-- (spring.flyway.enabled: false) and schema comes from ddl-auto: update.
-- Hibernate will add these columns on its own, but only as NULLABLE with no
-- default and no backfill -- it has no way to express "existing rows should
-- look like the Classic template". This script is what makes the schema
-- correct rather than merely present.
--
-- ORDER DOES NOT MATTER. Run it before or after the service has booted:
--   - run first  -> the columns already exist with defaults, and Hibernate's
--                   update pass sees them and does nothing.
--   - run second -> Hibernate has added nullable columns, this backfills them
--                   and applies the constraints.
-- Every statement is idempotent, so re-running it is safe.
--
-- HOW TO RUN (local docker compose):
--   docker compose exec -T postgres psql -U qrserve -d qrserve_menu \
--     < backend/menu-service/src/main/resources/db/manual/001-menu-template-structure.sql
--
-- Until this runs, the application is still correct but not constrained:
-- MenuTemplateEntity.normalised() resolves a NULL structural column to the
-- same default this script writes, so a customer menu renders identically
-- either way. The value of running it is that the database, not just the
-- application, guarantees a template is fully defined.

BEGIN;

-- 1. Columns. IF NOT EXISTS so this co-exists with whatever ddl-auto did.
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS layout_structure   VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS item_card_style    VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS show_images        BOOLEAN;
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS image_position     VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS image_aspect_ratio VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS font_family        VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS header_alignment   VARCHAR(20);
ALTER TABLE menu_templates ADD COLUMN IF NOT EXISTS show_cover_image   BOOLEAN;

-- 2. Backfill. These are the Classic-template values, chosen because they are
--    the closest structural equivalent of what every pre-migration template
--    actually rendered as: one column, bordered cards, a square thumbnail on
--    the left. An existing branch's menu therefore looks the same after this
--    migration as before it.
UPDATE menu_templates SET layout_structure   = 'SINGLE_COLUMN' WHERE layout_structure   IS NULL;
UPDATE menu_templates SET item_card_style    = 'CARD_BORDERED' WHERE item_card_style    IS NULL;
UPDATE menu_templates SET show_images        = TRUE            WHERE show_images        IS NULL;
UPDATE menu_templates SET image_position     = 'LEFT'          WHERE image_position     IS NULL;
UPDATE menu_templates SET image_aspect_ratio = 'SQUARE_1_1'    WHERE image_aspect_ratio IS NULL;
UPDATE menu_templates SET font_family        = 'SANS_SERIF'    WHERE font_family        IS NULL;
UPDATE menu_templates SET header_alignment   = 'CENTER'        WHERE header_alignment   IS NULL;
UPDATE menu_templates SET show_cover_image   = TRUE            WHERE show_cover_image   IS NULL;

-- 3. Defaults, so a row inserted by anything other than the JPA entity (a
--    fixture, a psql session, a future service) is still complete.
ALTER TABLE menu_templates ALTER COLUMN layout_structure   SET DEFAULT 'SINGLE_COLUMN';
ALTER TABLE menu_templates ALTER COLUMN item_card_style    SET DEFAULT 'CARD_BORDERED';
ALTER TABLE menu_templates ALTER COLUMN show_images        SET DEFAULT TRUE;
ALTER TABLE menu_templates ALTER COLUMN image_position     SET DEFAULT 'LEFT';
ALTER TABLE menu_templates ALTER COLUMN image_aspect_ratio SET DEFAULT 'SQUARE_1_1';
ALTER TABLE menu_templates ALTER COLUMN font_family        SET DEFAULT 'SANS_SERIF';
ALTER TABLE menu_templates ALTER COLUMN header_alignment   SET DEFAULT 'CENTER';
ALTER TABLE menu_templates ALTER COLUMN show_cover_image   SET DEFAULT TRUE;

-- 4. NOT NULL, now that every row has a value. Safe to re-run: applying
--    NOT NULL to an already-NOT NULL column is a no-op in Postgres.
ALTER TABLE menu_templates ALTER COLUMN layout_structure   SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN item_card_style    SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN show_images        SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN image_position     SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN image_aspect_ratio SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN font_family        SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN header_alignment   SET NOT NULL;
ALTER TABLE menu_templates ALTER COLUMN show_cover_image   SET NOT NULL;

-- 5. Value constraints. The enum names are the contract between this table and
--    MenuTemplateEntity; a typo written directly via psql would otherwise
--    reach the customer page as an unrenderable template. Dropped first so the
--    script can be re-run after an enum gains a value.
ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_layout_structure_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_layout_structure_chk
    CHECK (layout_structure IN ('GRID_2', 'GRID_3', 'SINGLE_COLUMN', 'TWO_COLUMN', 'COMPACT_LIST'));

ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_item_card_style_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_item_card_style_chk
    CHECK (item_card_style IN ('CARD_BORDERED', 'CARD_FLAT', 'ELEVATED_SHADOW', 'MINIMAL_DIVIDER'));

ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_image_position_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_image_position_chk
    CHECK (image_position IN ('TOP', 'LEFT', 'RIGHT', 'NONE'));

ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_image_aspect_ratio_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_image_aspect_ratio_chk
    CHECK (image_aspect_ratio IN ('SQUARE_1_1', 'LANDSCAPE_16_9', 'ROUNDED_AVATAR'));

ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_font_family_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_font_family_chk
    CHECK (font_family IN ('SANS_SERIF', 'SERIF', 'MODERN_MONO'));

ALTER TABLE menu_templates DROP CONSTRAINT IF EXISTS menu_templates_header_alignment_chk;
ALTER TABLE menu_templates ADD  CONSTRAINT menu_templates_header_alignment_chk
    CHECK (header_alignment IN ('LEFT', 'CENTER'));

COMMIT;

-- Verification query -- every row should come back fully populated:
--   SELECT key, layout_structure, item_card_style, show_images, image_position,
--          image_aspect_ratio, font_family, header_alignment, show_cover_image
--   FROM menu_templates ORDER BY key;
