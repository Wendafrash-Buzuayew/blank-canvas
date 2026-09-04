import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  resolveBranch,
  resolvePrimaryBranch,
  fetchBranchMenu,
  type DigitalMenuResolution,
} from '../lib/digitalMenu';

/**
 * Read-only digital menu view — no cart, no ordering (out of scope for the
 * phase-1 HLD rollout; see docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md).
 * Deliberately a new page, not a reuse of CustomerMenuPage, which carries
 * cart/order UI that belongs to the parked ordering flow.
 */
export function DigitalMenuPage() {
  const { merchantSlug, branchSlug } = useParams<{ merchantSlug: string; branchSlug?: string }>();

  if (!merchantSlug) {
    return <div role="alert">Menu not found.</div>;
  }

  if (!branchSlug) {
    return <PrimaryBranchRedirect merchantSlug={merchantSlug} />;
  }

  return <BranchMenu merchantSlug={merchantSlug} branchSlug={branchSlug} />;
}

function PrimaryBranchRedirect({ merchantSlug }: { merchantSlug: string }) {
  const [resolution, setResolution] = useState<DigitalMenuResolution | 'not-found' | null>(null);

  useEffect(() => {
    let cancelled = false;
    resolvePrimaryBranch(merchantSlug)
      .then((r) => { if (!cancelled) setResolution(r); })
      .catch(() => { if (!cancelled) setResolution('not-found'); });
    return () => { cancelled = true; };
  }, [merchantSlug]);

  if (resolution === null) {
    return <div>Loading menu…</div>;
  }
  if (resolution === 'not-found') {
    return <div role="alert">Menu coming soon.</div>;
  }
  return <Navigate replace to={`/m/${merchantSlug}/${resolution.branchSlug}`} />;
}

function BranchMenu({ merchantSlug, branchSlug }: { merchantSlug: string; branchSlug: string }) {
  const resolutionQuery = useQuery({
    queryKey: ['digital-menu-resolution', merchantSlug, branchSlug],
    queryFn: () => resolveBranch(merchantSlug, branchSlug),
  });

  const menuQuery = useQuery({
    queryKey: ['digital-menu-content', resolutionQuery.data?.branchId],
    queryFn: () => fetchBranchMenu(resolutionQuery.data!.branchId),
    enabled: !!resolutionQuery.data,
  });

  if (resolutionQuery.isError) {
    return <div role="alert">Menu not found.</div>;
  }
  if (resolutionQuery.isLoading || menuQuery.isLoading) {
    return <div>Loading menu…</div>;
  }
  if (menuQuery.isError) {
    return <div role="alert">Menu coming soon.</div>;
  }

  return (
    <div>
      <h1>{resolutionQuery.data?.branchName}</h1>
      {menuQuery.data?.categories.map((category) => (
        <section key={category.id}>
          <h2>{category.name}</h2>
          <ul>
            {category.items.map((item) => (
              <li key={item.id}>
                <strong>{item.name}</strong> —{' '}
                {item.effectivePrice < item.price ? (
                  <>
                    <span style={{ textDecoration: 'line-through', opacity: 0.6 }}>{item.price}</span>{' '}
                    <span>{item.effectivePrice}</span>
                  </>
                ) : (
                  item.price
                )}
                {item.description && <p>{item.description}</p>}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
