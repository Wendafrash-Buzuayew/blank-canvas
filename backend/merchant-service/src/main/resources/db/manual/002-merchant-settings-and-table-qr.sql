-- Merchant settings and table QR provisioning: the constraints ddl-auto cannot make.
--
-- Run against qrserve_merchant AFTER deploying the code change (the tables are
-- created by spring.jpa.hibernate.ddl-auto=update on startup; these indexes are not).
--
-- Why this is a manual file: spring.flyway.enabled is false, and ddl-auto ADDS
-- tables and columns but never creates a partial index and never enforces
-- uniqueness that involves a nullable column the way Postgres treats NULLs.
--
-- Safe on a populated database: both statements are IF NOT EXISTS and both are
-- additive. If either fails on duplicate data, that duplicate data is the bug.

-- 1. One merchant-wide settings row per merchant.
--
--    UNIQUE (merchant_id, branch_id) does NOT achieve this: in Postgres, NULLs are
--    distinct, so a merchant could accumulate any number of rows with branch_id
--    NULL and MerchantSettingsResolver would silently read whichever one the
--    planner returned first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_settings_merchant_wide
    ON merchant_settings (merchant_id)
    WHERE branch_id IS NULL;

-- 2. One settings row per branch.
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_settings_branch
    ON merchant_settings (merchant_id, branch_id)
    WHERE branch_id IS NOT NULL;

-- 3. One ACTIVE QR per table. A table with two active stickers means two terminal
--    labels in circulation and no way to know which one a guest scanned.
CREATE UNIQUE INDEX IF NOT EXISTS uq_table_qr_active
    ON table_qr (table_id)
    WHERE state = 'ACTIVE';
