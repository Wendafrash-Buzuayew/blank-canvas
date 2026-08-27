/**
 * Phase 1 / Phase 2 feature gating.
 *
 * Phase 1 is the lean M-PESA Merchant Mini App: Auth, Menu, a single QR,
 * a read-only public menu. Phase 2 (tables, waiters, kitchen, orders,
 * analytics) is fully preserved in the codebase but hidden behind this flag
 * until it's turned back on. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md.
 */

const MERCHANT_OWNER_ROLE = 'MERCHANT_OWNER';

/**
 * Parses the raw VITE_ENABLE_PHASE_2 env value. Exported as a pure function
 * (rather than reading import.meta.env directly everywhere) so it's
 * testable without mocking Vite's env injection.
 */
export function parseEnabledFlag(value: string | undefined): boolean {
  return value === 'true';
}

function readRawFlag(): string | undefined {
  // `import.meta.env` only exists under Vite. This module is also imported
  // by phase.test.ts, which runs under plain node via tsx, so reading it
  // unguarded throws at import time and takes the whole test file with it -
  // same guard as src/lib/tenant.ts's readBaseDomain().
  const env = (import.meta as ImportMeta & { env?: Record<string, string> }).env;
  return env?.VITE_ENABLE_PHASE_2;
}

const PHASE_2_ENABLED: boolean = parseEnabledFlag(readRawFlag());

/** Whether Phase 2 (tables, waiters, kitchen, orders, analytics) is active. */
export function isPhase2Enabled(): boolean {
  return PHASE_2_ENABLED;
}

/**
 * Whether `role` may use the app in the current phase. In Phase 1 only
 * MERCHANT_OWNER may log in; every other role (including SUPER_ADMIN) is
 * unauthorized until Phase 2 is re-enabled.
 */
export function isRoleAllowedInPhase(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): boolean {
  return phase2Enabled || role === MERCHANT_OWNER_ROLE;
}
