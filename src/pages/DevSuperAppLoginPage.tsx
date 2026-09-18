import React from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';

/**
 * Local-dev-only convenience: builds the same `superapp_token` LoginPage
 * already knows how to exchange (see src/lib/superApp.ts and
 * AuthContext.loginWithSuperAppToken) from plain msisdn/shortCode query
 * params, so a developer can simulate a Super App handoff by typing a URL
 * instead of hand-crafting the token JSON themselves.
 *
 * This page grants nothing by itself — it only assembles the token and
 * redirects to /login, which does the real exchange. The actual trust
 * boundary is server-side: DevFakeSuperAppAuthPort refuses that exchange
 * unless SUPERAPP_DEV_FAKE_ENABLED=true, which must never be set outside
 * local/dev/staging (see that class's doc comment). Registered only when
 * import.meta.env.DEV is true (see AppRouter.tsx) so it does not ship into a
 * production bundle at all.
 */
export const DevSuperAppLoginPage: React.FC = () => {
  const [params] = useSearchParams();
  const msisdn = params.get('msisdn');
  const shortCode = params.get('shortCode');

  if (!msisdn || !shortCode) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
        <div className="max-w-md space-y-3 text-center">
          <h1 className="text-title-m text-ink">Super App dev simulator</h1>
          <p className="text-body-m text-muted">Provide both query params, for example:</p>
          <code className="block break-all rounded-control border border-line bg-surface-2 px-3 py-2 text-label-s text-ink">
            /dev/superapp-login?msisdn=251911123456&amp;shortCode=8319389
          </code>
        </div>
      </div>
    );
  }

  // Field names match SuperAppMerchantClaim exactly — DevFakeSuperAppAuthPort
  // deserialises this raw JSON directly (see its Javadoc).
  const token = JSON.stringify({ merchantShortCode: shortCode, msisdn });
  return <Navigate to={`/login?superapp_token=${encodeURIComponent(token)}`} replace />;
};
