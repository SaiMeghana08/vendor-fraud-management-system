-- =====================================================================
-- V1 : Core schema for the Fraudulent Vendor Bank-Change Detection
--      Workflow.  MySQL 8 / InnoDB / utf8mb4.
--
-- Design notes
--  * Enumerated values are stored as VARCHAR and guarded by CHECK
--    constraints so that the JPA enum mapping and the database agree.
--  * Master records (users, vendors, vendor bank accounts) carry
--    soft-delete columns.  Transactional records never do - they use
--    lifecycle statuses instead.
--  * `active_marker` columns exist purely to emulate a partial unique
--    index: the column holds 1 while the row is active and NULL once it
--    is not, and MySQL permits many NULLs in a UNIQUE index.
--  * Optimistic locking columns are named `version` (JPA @Version).
-- =====================================================================

-- ------------------------------------------------------------------ --
-- ROLES
-- ------------------------------------------------------------------ --
CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN
        ('MAKER','APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------------ --
-- USERS  (soft-deletable master record)
-- ------------------------------------------------------------------ --
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(60)  NOT NULL,
    email         VARCHAR(160) NOT NULL,
    full_name     VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                               ON UPDATE CURRENT_TIMESTAMP(6),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_users_active ON users (is_active);

-- ------------------------------------------------------------------ --
-- USER_ROLES  (many-to-many; multiple roles per user are supported)
-- ------------------------------------------------------------------ --
CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- ------------------------------------------------------------------ --
-- VENDORS  (soft-deletable master record)
-- ------------------------------------------------------------------ --
CREATE TABLE vendors (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    vendor_code    VARCHAR(40)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    tax_reference  VARCHAR(60)  NULL,
    contact_email  VARCHAR(160) NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at     DATETIME(6)  NULL,
    deleted_by     BIGINT       NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_vendors PRIMARY KEY (id),
    CONSTRAINT uk_vendors_code UNIQUE (vendor_code),
    CONSTRAINT fk_vendors_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_vendors_active_name ON vendors (is_active, name);

-- ------------------------------------------------------------------ --
-- VENDOR_BANK_ACCOUNTS
--   Holds both the currently active banking record and the full
--   history of superseded records.  Synthetic references only.
-- ------------------------------------------------------------------ --
CREATE TABLE vendor_bank_accounts (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    vendor_id           BIGINT      NOT NULL,
    account_holder_name VARCHAR(160) NOT NULL,
    bank_name           VARCHAR(160) NOT NULL,
    account_reference   VARCHAR(60)  NOT NULL,
    routing_code        VARCHAR(40)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    active_marker       TINYINT      NULL,
    activated_at        DATETIME(6)  NULL,
    superseded_at       DATETIME(6)  NULL,
    source_request_id   BIGINT       NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at          DATETIME(6)  NULL,
    deleted_by          BIGINT       NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_vendor_bank_accounts PRIMARY KEY (id),
    CONSTRAINT fk_vba_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_vba_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    CONSTRAINT ck_vba_status CHECK (status IN ('ACTIVE','SUPERSEDED')),
    -- at most one ACTIVE banking record per vendor
    CONSTRAINT uk_vba_one_active UNIQUE (vendor_id, active_marker)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_vba_vendor_status ON vendor_bank_accounts (vendor_id, status);

-- ------------------------------------------------------------------ --
-- BANK_CHANGE_REQUESTS
-- ------------------------------------------------------------------ --
CREATE TABLE bank_change_requests (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    request_reference           VARCHAR(40)  NOT NULL,
    vendor_id                   BIGINT       NOT NULL,
    maker_id                    BIGINT       NOT NULL,
    current_bank_account_id     BIGINT       NULL,
    proposed_account_holder_name VARCHAR(160) NOT NULL,
    proposed_bank_name          VARCHAR(160) NOT NULL,
    proposed_account_reference   VARCHAR(60) NOT NULL,
    proposed_routing_code       VARCHAR(40)  NOT NULL,
    reason                      VARCHAR(1000) NOT NULL,
    evidence_reference          VARCHAR(120) NOT NULL,
    status                      VARCHAR(30)  NOT NULL,
    open_marker                 TINYINT      NULL,
    cooling_off_hours           INT          NULL,
    cooling_off_start_at        DATETIME(6)  NULL,
    cooling_off_end_at          DATETIME(6)  NULL,
    cooling_off_override_id     BIGINT       NULL,
    activated_bank_account_id   BIGINT       NULL,
    activated_at                DATETIME(6)  NULL,
    terminal_reason             VARCHAR(1000) NULL,
    created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_bcr PRIMARY KEY (id),
    CONSTRAINT uk_bcr_reference UNIQUE (request_reference),
    -- one open bank-change request per vendor (duplicate-request guard)
    CONSTRAINT uk_bcr_one_open_per_vendor UNIQUE (vendor_id, open_marker),
    CONSTRAINT fk_bcr_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_bcr_maker FOREIGN KEY (maker_id) REFERENCES users (id),
    CONSTRAINT fk_bcr_current_account FOREIGN KEY (current_bank_account_id)
        REFERENCES vendor_bank_accounts (id),
    CONSTRAINT fk_bcr_activated_account FOREIGN KEY (activated_bank_account_id)
        REFERENCES vendor_bank_accounts (id),
    CONSTRAINT ck_bcr_status CHECK (status IN
        ('PENDING_FIRST_APPROVAL','PENDING_SECOND_APPROVAL','COOLING_OFF',
         'ACTIVATED','REJECTED','CANCELLED','ACTIVATION_BLOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_bcr_status ON bank_change_requests (status);
CREATE INDEX ix_bcr_maker ON bank_change_requests (maker_id, created_at);
CREATE INDEX ix_bcr_cooling_off ON bank_change_requests (status, cooling_off_end_at);

-- ------------------------------------------------------------------ --
-- APPROVAL_TASKS
-- ------------------------------------------------------------------ --
CREATE TABLE approval_tasks (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    bank_change_request_id  BIGINT       NOT NULL,
    stage                   VARCHAR(10)  NOT NULL,
    assignee_id             BIGINT       NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    open_marker             TINYINT      NULL,
    decision_comment        VARCHAR(1000) NULL,
    assigned_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at              DATETIME(6)  NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_tasks PRIMARY KEY (id),
    CONSTRAINT fk_at_request FOREIGN KEY (bank_change_request_id)
        REFERENCES bank_change_requests (id),
    CONSTRAINT fk_at_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT ck_at_stage CHECK (stage IN ('FIRST','SECOND')),
    CONSTRAINT ck_at_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','REASSIGNED')),
    -- at most one PENDING task per (request, stage): duplicate-assignment guard
    CONSTRAINT uk_at_one_open UNIQUE (bank_change_request_id, stage, open_marker)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_at_assignee_status ON approval_tasks (assignee_id, status);
CREATE INDEX ix_at_request_stage ON approval_tasks (bank_change_request_id, stage);

-- ------------------------------------------------------------------ --
-- APPROVAL_ASSIGNMENT_HISTORY  (append-only assignment trail)
-- ------------------------------------------------------------------ --
CREATE TABLE approval_assignment_history (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    approval_task_id     BIGINT       NOT NULL,
    action               VARCHAR(20)  NOT NULL,
    previous_assignee_id BIGINT       NULL,
    new_assignee_id      BIGINT       NOT NULL,
    actor_id             BIGINT       NULL,
    reason               VARCHAR(1000) NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_aah PRIMARY KEY (id),
    CONSTRAINT fk_aah_task FOREIGN KEY (approval_task_id) REFERENCES approval_tasks (id),
    CONSTRAINT fk_aah_prev FOREIGN KEY (previous_assignee_id) REFERENCES users (id),
    CONSTRAINT fk_aah_new FOREIGN KEY (new_assignee_id) REFERENCES users (id),
    CONSTRAINT fk_aah_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_aah_action CHECK (action IN ('ASSIGNED','REASSIGNED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_aah_task ON approval_assignment_history (approval_task_id);

-- ------------------------------------------------------------------ --
-- PAYMENTS
-- ------------------------------------------------------------------ --
CREATE TABLE payments (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    payment_reference       VARCHAR(40)  NOT NULL,
    vendor_id               BIGINT       NOT NULL,
    vendor_bank_account_id  BIGINT       NULL,
    amount                  DECIMAL(18,2) NOT NULL,
    currency                CHAR(3)      NOT NULL,
    due_date                DATE         NOT NULL,
    description             VARCHAR(500) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_by              BIGINT       NOT NULL,
    processed_at            DATETIME(6)  NULL,
    processed_by            BIGINT       NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
    CONSTRAINT fk_payments_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_payments_account FOREIGN KEY (vendor_bank_account_id)
        REFERENCES vendor_bank_accounts (id),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_payments_processed_by FOREIGN KEY (processed_by) REFERENCES users (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('CREATED','ON_HOLD','PROCESSED','CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_payments_vendor_status ON payments (vendor_id, status);
CREATE INDEX ix_payments_due_date ON payments (due_date);

-- ------------------------------------------------------------------ --
-- PAYMENT_HOLDS
-- ------------------------------------------------------------------ --
CREATE TABLE payment_holds (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id              BIGINT       NOT NULL,
    bank_change_request_id  BIGINT       NULL,
    reason                  VARCHAR(500) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    active_marker           TINYINT      NULL,
    release_justification   VARCHAR(1000) NULL,
    released_by             BIGINT       NULL,
    released_at             DATETIME(6)  NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_holds PRIMARY KEY (id),
    CONSTRAINT fk_ph_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_ph_request FOREIGN KEY (bank_change_request_id)
        REFERENCES bank_change_requests (id),
    CONSTRAINT fk_ph_released_by FOREIGN KEY (released_by) REFERENCES users (id),
    CONSTRAINT ck_ph_status CHECK (status IN ('ACTIVE','RELEASED')),
    -- a payment carries at most one ACTIVE hold per bank-change request
    CONSTRAINT uk_ph_one_active UNIQUE (payment_id, bank_change_request_id, active_marker)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_ph_payment_status ON payment_holds (payment_id, status);
CREATE INDEX ix_ph_request ON payment_holds (bank_change_request_id);

-- ------------------------------------------------------------------ --
-- EMERGENCY_OVERRIDES
-- ------------------------------------------------------------------ --
CREATE TABLE emergency_overrides (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    override_reference      VARCHAR(40)  NOT NULL,
    bank_change_request_id  BIGINT       NOT NULL,
    requested_by            BIGINT       NOT NULL,
    justification           VARCHAR(1000) NOT NULL,
    evidence_reference      VARCHAR(120) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    open_marker             TINYINT      NULL,
    decided_by              BIGINT       NULL,
    decided_at              DATETIME(6)  NULL,
    decision_comment        VARCHAR(1000) NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_eo PRIMARY KEY (id),
    CONSTRAINT uk_eo_reference UNIQUE (override_reference),
    CONSTRAINT uk_eo_one_open UNIQUE (bank_change_request_id, open_marker),
    CONSTRAINT fk_eo_request FOREIGN KEY (bank_change_request_id)
        REFERENCES bank_change_requests (id),
    CONSTRAINT fk_eo_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_eo_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT ck_eo_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_eo_status ON emergency_overrides (status);

-- ------------------------------------------------------------------ --
-- NOTIFICATIONS  (private to the recipient)
-- ------------------------------------------------------------------ --
CREATE TABLE notifications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id   BIGINT       NOT NULL,
    type           VARCHAR(50)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(40)  NULL,
    reference_id   BIGINT       NULL,
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at        DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, is_read, created_at);

-- ------------------------------------------------------------------ --
-- AUDIT_LOGS  (append-only; no update/delete endpoints exist)
-- ------------------------------------------------------------------ --
CREATE TABLE audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id       BIGINT       NULL,
    actor_username VARCHAR(60)  NOT NULL,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      BIGINT       NULL,
    workflow_state VARCHAR(40)  NULL,
    reason         VARCHAR(1000) NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_created ON audit_logs (created_at);

-- ------------------------------------------------------------------ --
-- REFERENCE_SEQUENCES
--   Backs concurrency-safe, gap-tolerant reference generation.
--   Never MAX(id)+1: rows are locked with SELECT ... FOR UPDATE.
-- ------------------------------------------------------------------ --
CREATE TABLE reference_sequences (
    sequence_name VARCHAR(40) NOT NULL,
    prefix        VARCHAR(10) NOT NULL,
    year_part     INT         NOT NULL,
    next_value    BIGINT      NOT NULL DEFAULT 1,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                              ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reference_sequences PRIMARY KEY (sequence_name),
    CONSTRAINT ck_rs_next_value CHECK (next_value > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
