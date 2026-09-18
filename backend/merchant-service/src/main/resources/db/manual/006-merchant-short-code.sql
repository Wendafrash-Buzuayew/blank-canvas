-- Adds the Safaricom till/short code column menu-service's ETHQR proxy
-- (GET /api/payment/ethqr) reads as `accountNumber`.
--
-- WHY HAND-RUN: Flyway is disabled across the backend
-- (spring.flyway.enabled: false); schema comes from ddl-auto: update.
-- Hibernate would add this column on its own on next startup regardless
-- (it's nullable), so this script is not strictly required — it exists so a
-- fresh environment provisioned from SQL matches one provisioned by
-- Hibernate, same as 004-merchant-branding.sql.
--
-- NULLABLE ON PURPOSE: a merchant created the ordinary way (not via Super
-- App token exchange) has no short code until one is set directly. The
-- ETHQR endpoint returns a clear error rather than a broken QR when this is
-- null — see PaymentController/SafaricomEthQrService in menu-service.

BEGIN;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS short_code VARCHAR(30);

COMMIT;

-- Verification:
--   SELECT slug, name, short_code FROM merchants;
--
-- To try the ETHQR path locally for a merchant not created via Super App:
--   UPDATE merchants SET short_code = '8319389' WHERE slug = 'sunrise-coffee';
