-- Uniqueness the application now checks but cannot guarantee under concurrency.
--
-- Run against qrserve_merchant AFTER deploying the code change. Both statements
-- are IF NOT EXISTS and additive. If either fails on duplicate data, that
-- duplicate data is the bug: resolve it by hand, then re-run.

-- 1. One table_number per branch (TableEntity's @UniqueConstraint; ddl-auto may
--    already have added it, hence IF NOT EXISTS on an equivalent index).
CREATE UNIQUE INDEX IF NOT EXISTS uk_tables_branch_table_number
    ON tables (branch_id, table_number);

-- 2. One ACTIVE waiter assignment per table. A partial index cannot be expressed
--    in JPA, so ddl-auto never creates it; TableAssignmentService maps a violation
--    to 409.
CREATE UNIQUE INDEX IF NOT EXISTS uq_table_assignment_active
    ON table_assignments (table_id)
    WHERE status = 'ACTIVE';
