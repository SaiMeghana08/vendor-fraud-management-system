-- ---------------------------------------------------------------------
-- Creates the database and application user.
--
-- Run as root once, before starting the backend for the first time.
-- Flyway then creates every table; see V1-V3 under
-- backend/src/main/resources/db/migration.
--
-- Replace the password below before running. No password used by this
-- project is stored in version control.
-- ---------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS vendor_fraud_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'vendor_fraud_app'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_YOUR_OWN_PASSWORD';

GRANT ALL PRIVILEGES ON vendor_fraud_db.* TO 'vendor_fraud_app'@'localhost';

-- Optional: a separate database for running the test suite against MySQL.
CREATE DATABASE IF NOT EXISTS vendor_fraud_test
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON vendor_fraud_test.* TO 'vendor_fraud_app'@'localhost';

FLUSH PRIVILEGES;
