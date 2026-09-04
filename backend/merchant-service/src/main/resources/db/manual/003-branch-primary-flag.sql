-- Branch primary flag: exactly one primary branch per merchant.
--
-- Run against qrserve_merchant AFTER deploying the code change (the column
-- is added by spring.jpa.hibernate.ddl-auto=update on startup; this index
-- is not — see backend/merchant-service/.../db/manual/002-...sql for why).
--
-- Safe on a populated database: additive, IF NOT EXISTS. Existing branches
-- default is_primary=false; run the app-level backfill (Task 13) to set the
-- first branch per merchant to true before relying on this index in
-- production traffic.
CREATE UNIQUE INDEX IF NOT EXISTS branches_one_primary_per_merchant
    ON branches (merchant_id)
    WHERE is_primary;
