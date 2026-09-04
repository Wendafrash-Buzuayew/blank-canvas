import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  resolveBranch,
  resolvePrimaryBranch,
  fetchBranchMenu,
  type DigitalMenuResolution,
  type MenuTemplateStyle,
} from '../lib/digitalMenu';

/**
 * A small, team-curated, fixed set of visual presentations — the merchant
 * picks one (MenuBuilderPage), not a merchant-authored/customizable
 * template system. Each entry is just a set of Tailwind class tokens
 * applied to the same page structure below.
 */
const TEMPLATES: Record<MenuTemplateStyle, {
  page: string; header: string; title: string; categoryHeading: string;
  row: string; itemName: string; price: string; strikePrice: string; description: string;
}> = {
  CLASSIC: {
    page: 'min-h-screen bg-white text-slate-900',
    header: 'px-6 py-8 text-center border-b-4 border-[#E60028]',
    title: 'text-3xl font-black',
    categoryHeading: 'text-lg font-extrabold text-[#E60028] uppercase tracking-wide mt-8 mb-2 px-6',
    row: 'flex justify-between gap-4 px-6 py-4 border-b border-slate-100',
    itemName: 'font-bold',
    price: 'font-black',
    strikePrice: 'text-slate-400 line-through text-sm',
    description: 'text-sm text-slate-500 mt-1',
  },
  MODERN_DARK: {
    page: 'min-h-screen bg-slate-950 text-white',
    header: 'px-6 py-8 text-center border-b border-slate-800',
    title: 'text-3xl font-black',
    categoryHeading: 'text-lg font-extrabold text-red-400 uppercase tracking-wide mt-8 mb-2 px-6',
    row: 'flex justify-between gap-4 px-6 py-4 border-b border-slate-800',
    itemName: 'font-bold',
    price: 'font-black',
    strikePrice: 'text-slate-500 line-through text-sm',
    description: 'text-sm text-slate-400 mt-1',
  },
  VIBRANT: {
    page: 'min-h-screen bg-gradient-to-b from-amber-50 via-white to-white text-slate-900',
    header: 'px-6 py-10 text-center',
    title: 'text-4xl font-black text-amber-600',
    categoryHeading: 'text-lg font-extrabold text-amber-600 uppercase tracking-wide mt-8 mb-3 px-6',
    row: 'flex justify-between gap-4 mx-4 mb-3 p-4 bg-white rounded-2xl border-2 border-amber-100 shadow-sm',
    itemName: 'font-bold',
    price: 'font-black text-amber-700',
    strikePrice: 'text-slate-400 line-through text-sm',
    description: 'text-sm text-slate-500 mt-1',
  },
};

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

  const template = TEMPLATES[menuQuery.data?.templateStyle ?? 'CLASSIC'];

  return (
    <div className={template.page}>
      <header className={template.header}>
        <h1 className={template.title}>{resolutionQuery.data?.branchName}</h1>
      </header>
      {menuQuery.data?.categories.map((category) => (
        <section key={category.id}>
          <h2 className={template.categoryHeading}>{category.name}</h2>
          {category.items.map((item) => (
            <div key={item.id} className={template.row}>
              <div className="min-w-0">
                <div className={template.itemName}>{item.name}</div>
                {item.description && <p className={template.description}>{item.description}</p>}
              </div>
              <div className="shrink-0 text-right">
                {item.effectivePrice < item.price ? (
                  <>
                    <div className={template.strikePrice}>{item.price}</div>
                    <div className={template.price}>{item.effectivePrice}</div>
                  </>
                ) : (
                  <div className={template.price}>{item.price}</div>
                )}
              </div>
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}
