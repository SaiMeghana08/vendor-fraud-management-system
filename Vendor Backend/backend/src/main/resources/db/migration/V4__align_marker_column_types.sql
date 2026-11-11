ALTER TABLE vendor_bank_accounts
    MODIFY COLUMN active_marker INT NULL;

ALTER TABLE bank_change_requests
    MODIFY COLUMN open_marker INT NULL;

ALTER TABLE approval_tasks
    MODIFY COLUMN open_marker INT NULL;

ALTER TABLE payment_holds
    MODIFY COLUMN active_marker INT NULL;

ALTER TABLE emergency_overrides
    MODIFY COLUMN open_marker INT NULL;
