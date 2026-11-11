-- =====================================================================
-- V3 : Synthetic demonstration data.
--
--      Vendors and their ACTIVE banking records.  Every account
--      reference and routing code below is fictional and exists only
--      for demonstration; none of it corresponds to a real bank,
--      account or payment rail.
--
--      Users are NOT seeded here.  Passwords must never live in a
--      migration file - users are created by the controlled bootstrap
--      component (see DevDataBootstrap) which hashes a password taken
--      from configuration with BCrypt.
-- =====================================================================

INSERT INTO vendors (vendor_code, name, tax_reference, contact_email, is_active) VALUES
    ('VND-1001', 'Northwind Industrial Supplies', 'TAXREF-NW-0001', 'ap@northwind.example',   TRUE),
    ('VND-1002', 'Helios Facility Services',     'TAXREF-HL-0002', 'billing@helios.example',  TRUE),
    ('VND-1003', 'Meridian Logistics Partners',  'TAXREF-MR-0003', 'finance@meridian.example',TRUE),
    ('VND-1004', 'Cobalt IT Consulting',         'TAXREF-CB-0004', 'accounts@cobalt.example', TRUE),
    ('VND-1005', 'Larkspur Print and Media',     'TAXREF-LK-0005', 'hello@larkspur.example',  TRUE),
    ('VND-9001', 'Obsolete Trading Co (retired)','TAXREF-OB-9001', 'void@obsolete.example',   FALSE);

-- The retired vendor is soft-deleted so that it can be used to prove
-- that inactive vendors never appear in active dropdowns.
UPDATE vendors
   SET deleted_at = CURRENT_TIMESTAMP(6)
 WHERE vendor_code = 'VND-9001';

INSERT INTO vendor_bank_accounts
    (vendor_id, account_holder_name, bank_name, account_reference, routing_code,
     status, active_marker, activated_at)
SELECT v.id, x.holder, x.bank, x.acct, x.routing, 'ACTIVE', 1, CURRENT_TIMESTAMP(6)
  FROM vendors v
  JOIN (
        SELECT 'VND-1001' AS code, 'Northwind Industrial Supplies' AS holder,
               'Synthetic Bank of Avalon' AS bank, 'SYN-AC-100001' AS acct, 'SYNRT-0001' AS routing
  UNION SELECT 'VND-1002', 'Helios Facility Services',
               'Synthetic Bank of Avalon', 'SYN-AC-100002', 'SYNRT-0001'
  UNION SELECT 'VND-1003', 'Meridian Logistics Partners',
               'Fictional Mutual Bank',    'SYN-AC-100003', 'SYNRT-0002'
  UNION SELECT 'VND-1004', 'Cobalt IT Consulting',
               'Fictional Mutual Bank',    'SYN-AC-100004', 'SYNRT-0002'
  UNION SELECT 'VND-1005', 'Larkspur Print and Media',
               'Example Credit Union',     'SYN-AC-100005', 'SYNRT-0003'
       ) x ON x.code = v.vendor_code;
