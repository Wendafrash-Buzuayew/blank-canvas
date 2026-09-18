-- Adds the two brand-presentation columns the customer digital menu header
-- uses: a wide cover banner and a one-line tagline. `logo_url` already exists.
--
-- WHY HAND-RUN: Flyway is disabled across the backend
-- (spring.flyway.enabled: false); schema comes from ddl-auto: update.
-- Hibernate will add both columns on its own since they are nullable, so this
-- script is not strictly required to make the feature work - it exists so the
-- column length constraint on `tagline` is real in the database rather than
-- only in the entity, and so a fresh environment provisioned from SQL matches
-- one provisioned by Hibernate.
--
-- Numbered 004: 003 is already taken by 003-branch-primary-flag.sql.
--
-- HOW TO RUN (local docker compose):
--   docker compose exec -T postgres psql -U postgres -d qrserve_merchant \
--     < backend/merchant-service/src/main/resources/db/manual/003-merchant-branding.sql
--
-- Both columns are NULLABLE ON PURPOSE and are expected to stay null for some
-- time. Nothing uploads a cover yet, and no merchant has set a logo. The
-- customer menu handles all three being absent: it falls back to the branch's
-- first dish photo for the banner (see TemplatedMenu.firstDishImage) and
-- simply omits the logo and tagline. Adding NOT NULL here would break that.

BEGIN;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(512);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS tagline         VARCHAR(120);

-- Length guard on the tagline. It is rendered on one line under the menu
-- title, over a photo, on a 375px phone: past ~120 characters it wraps into
-- the dish list and stops being a tagline.
--
-- NOTE: VARCHAR(120) above is what actually rejects an over-long value today
-- ("value too long for type character varying(120)" fires before any CHECK).
-- This constraint is therefore belt-and-braces, and earns its place only if
-- the column is ever widened - at which point it keeps the header contract
-- intact. Dropped first so this script can be re-run after a length change.
ALTER TABLE merchants DROP CONSTRAINT IF EXISTS merchants_tagline_len_chk;
ALTER TABLE merchants ADD  CONSTRAINT merchants_tagline_len_chk
    CHECK (tagline IS NULL OR char_length(tagline) <= 120);

COMMIT;

-- Verification:
--   SELECT slug, name, logo_url, cover_image_url, tagline FROM merchants;
--
-- To give the local Sunrise Coffee merchant a tagline for testing:
--   UPDATE merchants SET tagline = 'Freshly roasted, every morning'
--   WHERE slug = 'sunrise-coffee';
