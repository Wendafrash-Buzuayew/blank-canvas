-- Adds the subscription-tier column BranchService.createBranch enforces the
-- 1-branch Free-tier limit against.
--
-- WHY HAND-RUN: Flyway is disabled across the backend
-- (spring.flyway.enabled: false); schema comes from ddl-auto: update.
-- Hibernate WOULD add this column on its own on next startup regardless (the
-- entity's columnDefinition carries the same default), so this script is not
-- strictly required — it exists so a fresh environment provisioned from SQL
-- matches one provisioned by Hibernate, same as 004-merchant-branding.sql.
--
-- NOT NULL WITH A DEFAULT IN THE SAME STATEMENT, unlike the nullable columns
-- 004 added: every existing merchant row needs a real tier value, and this
-- form backfills them to 'FREE' as part of adding the column, in one
-- statement, rather than a separate UPDATE afterwards.

BEGIN;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS tier VARCHAR(10) NOT NULL DEFAULT 'FREE';

ALTER TABLE merchants DROP CONSTRAINT IF EXISTS merchants_tier_chk;
ALTER TABLE merchants ADD  CONSTRAINT merchants_tier_chk CHECK (tier IN ('FREE', 'PRO'));

COMMIT;

-- Verification:
--   SELECT slug, name, tier FROM merchants;
--
-- To try the Pro path locally without a billing flow:
--   UPDATE merchants SET tier = 'PRO' WHERE slug = 'sunrise-coffee';
