-- =====================================================================
-- V2 : Reference data - the four business roles and the reference
--      sequence rows used by the backend reference generators.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('MAKER',            'Raises vendor bank-change requests'),
    ('APPROVER',         'Independently approves or rejects bank-change requests'),
    ('ACCOUNTS_PAYABLE', 'Manages payments, payment holds and override requests'),
    ('ADMINISTRATOR',    'Decides emergency overrides, monitors exceptions, reads audit');

INSERT INTO reference_sequences (sequence_name, prefix, year_part, next_value) VALUES
    ('BANK_CHANGE_REQUEST', 'BCR', 2026, 1),
    ('PAYMENT',             'PAY', 2026, 1),
    ('EMERGENCY_OVERRIDE',  'EOV', 2026, 1);
