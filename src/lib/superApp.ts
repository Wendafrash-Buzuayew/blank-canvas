/**
 * Reads the M-PESA Super App handoff token from the URL.
 *
 * No real Super App integration contract exists yet - this query param is a
 * placeholder for whatever the real container hands over on launch, matching
 * the backend's dev-fake SuperAppAuthPort. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
const SUPER_APP_TOKEN_PARAM = 'superapp_token';

/**
 * @param search defaults to the real page's query string; overridable so this
 *   is testable under plain node (this module has no other browser dependency).
 */
export function getSuperAppToken(
  search: string = typeof window !== 'undefined' ? window.location.search : '',
): string | null {
  const params = new URLSearchParams(search);
  const token = params.get(SUPER_APP_TOKEN_PARAM);
  return token && token.trim() ? token : null;
}
