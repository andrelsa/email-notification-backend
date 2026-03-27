-- =============================================
-- V2: Add locked_until to retry_control
--
-- Purpose: prevent concurrent scheduler instances from processing the same
-- retry record simultaneously (duplicate email sends).
--
-- A scheduler instance atomically sets locked_until = now + lock_ttl before
-- processing a row.  If another instance races for the same row it sees the
-- lock and skips (via SKIP LOCKED).  The lock expires automatically, so a
-- crashed instance never permanently strands a record.
-- =============================================

ALTER TABLE retry_control
    ADD COLUMN locked_until TIMESTAMP;

COMMENT ON COLUMN retry_control.locked_until IS
    'Set to now() + lock_ttl when a scheduler instance claims this row. '
    'Expires automatically; NULL means the row is available for processing.';

CREATE INDEX idx_retry_control_locked_until ON retry_control (locked_until);

