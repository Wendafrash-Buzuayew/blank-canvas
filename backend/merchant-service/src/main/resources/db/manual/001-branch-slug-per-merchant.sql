-- Branch slug: globally unique -> unique per merchant.
--
-- Run against qrserve_merchant BEFORE deploying the code change.
--
-- Why this is a manual file rather than a migration: spring.flyway.enabled is
-- false and spring.jpa.hibernate.ddl-auto is `update`, which ADDS constraints but
-- never drops an existing one. Without this, an already-deployed database keeps
-- the old global unique index and the second tenant to name a branch "Main"
-- still gets a 500. (Introducing Flyway baselines is tracked separately as item
-- 7.5 of docs/superpowers/plans/2026-08-18-codebase-review-remediation.md.)
--
-- Safe on a populated database: loosening a uniqueness constraint cannot be
-- violated by existing rows, so no backfill and no downtime window are needed.

-- 1. Find the existing constraint. Hibernate generates its name, so it differs
--    per database and cannot be hardcoded here:
--
--      SELECT conname
--      FROM pg_constraint
--      WHERE conrelid = 'branches'::regclass
--        AND contype = 'u'
--        AND pg_get_constraintdef(oid) LIKE '%(slug)%';

-- 2. Drop it. The IF EXISTS covers the case where it was already named:
ALTER TABLE branches DROP CONSTRAINT IF EXISTS uk_branches_slug;

-- 3. If step 2 dropped nothing, drop the generated name found in step 1:
--      ALTER TABLE branches DROP CONSTRAINT <conname_from_step_1>;

-- 4. Add the per-merchant constraint. Matches
--    BranchEntity @UniqueConstraint(name = "uk_branches_merchant_slug").
ALTER TABLE branches
    ADD CONSTRAINT uk_branches_merchant_slug UNIQUE (merchant_id, slug);

-- 5. Verify: this must return exactly one row, naming merchant_id and slug.
--      SELECT conname, pg_get_constraintdef(oid)
--      FROM pg_constraint
--      WHERE conrelid = 'branches'::regclass AND contype = 'u';
