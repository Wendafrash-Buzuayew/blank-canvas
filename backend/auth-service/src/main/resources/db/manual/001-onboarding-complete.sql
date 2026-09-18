-- Onboarding-complete flag for the Super App merchant handshake.
--
-- WHY HAND-RUN: Flyway is disabled across the backend
-- (spring.flyway.enabled: false); schema comes from ddl-auto: update.
-- Hibernate will add the column on its own, but this script backfills a real
-- DEFAULT so a fresh environment provisioned from SQL matches one provisioned
-- by Hibernate, and so the column is NOT NULL from day one rather than
-- relying on every INSERT to remember the flag.
--
-- Every account created through the ordinary email/password path collects
-- its business profile (name, city, address, category) at creation time, so
-- it defaults to TRUE. A Super App login now only ever carries a merchant
-- short code and an MSISDN (see SuperAppMerchantClaim); the app-level code
-- sets this to FALSE at the moment it auto-provisions a new merchant from
-- that handshake, gating the frontend behind an onboarding form until the
-- merchant fills in the rest - see AuthController#completeOnboarding.
--
-- HOW TO RUN (local docker compose):
--   docker compose exec -T postgres psql -U postgres -d qrserve_auth \
--     < backend/auth-service/src/main/resources/db/manual/001-onboarding-complete.sql

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_complete BOOLEAN NOT NULL DEFAULT true;

COMMIT;

-- Verification:
--   SELECT email, super_app_merchant_ref, onboarding_complete FROM users;
