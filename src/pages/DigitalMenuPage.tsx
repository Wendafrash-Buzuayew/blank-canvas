import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  resolveBranch,
  resolvePrimaryBranch,
  fetchBranchMenu,
  fetchReviewSummary,
  submitReview,
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
  const branchId = resolutionQuery.data?.branchId;

  return (
    <div className={template.page}>
      <header className={template.header}>
        <h1 className={template.title}>{resolutionQuery.data?.branchName}</h1>
        {branchId != null && <RatingBadge branchId={branchId} />}
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
      {branchId != null && <ReviewForm branchId={branchId} />}
    </div>
  );
}

function RatingBadge({ branchId }: { branchId: number }) {
  const summaryQuery = useQuery({
    queryKey: ['digital-menu-review-summary', branchId],
    queryFn: () => fetchReviewSummary(branchId),
  });

  if (!summaryQuery.data || summaryQuery.data.count === 0) return null;
  return (
    <p className="mt-1 text-sm opacity-80">
      ★ {summaryQuery.data.averageRating.toFixed(1)} · {summaryQuery.data.count} review{summaryQuery.data.count === 1 ? '' : 's'}
    </p>
  );
}

/** localStorage flag only — a soft nudge not to re-prompt on the same device, not a hard limit (a review has no other identity to dedupe on in phase 1). */
function alreadyReviewedKey(branchId: number) {
  return `qrserve.reviewed-branch.${branchId}`;
}

function ReviewForm({ branchId }: { branchId: number }) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(() => {
    try { return localStorage.getItem(alreadyReviewedKey(branchId)) === 'true'; }
    catch { return false; }
  });

  if (done) {
    return (
      <div className="px-6 py-8 text-center text-sm opacity-70">Thanks for your feedback!</div>
    );
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (rating < 1) { setError('Please select a rating.'); return; }
    setSubmitting(true);
    setError(null);
    try {
      await submitReview(branchId, { rating, comment: comment.trim() || undefined });
      try { localStorage.setItem(alreadyReviewedKey(branchId), 'true'); } catch { /* ignore */ }
      queryClient.invalidateQueries({ queryKey: ['digital-menu-review-summary', branchId] });
      setDone(true);
    } catch {
      setError('Could not submit your review — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="px-6 py-8 space-y-3">
      <h2 className="font-bold">Rate your visit</h2>
      <div className="flex gap-1" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={rating === n}
            aria-label={`${n} star${n === 1 ? '' : 's'}`}
            onClick={() => setRating(n)}
            className="text-2xl leading-none"
            style={{ opacity: n <= rating ? 1 : 0.3 }}
          >
            ★
          </button>
        ))}
      </div>
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="Tell us about your visit (optional)"
        rows={3}
        className="w-full rounded-lg border border-current/20 bg-transparent p-2 text-sm"
      />
      {error && <p role="alert" className="text-sm text-red-500">{error}</p>}
      <button type="submit" disabled={submitting} className="rounded-lg bg-current/10 px-4 py-2 text-sm font-bold disabled:opacity-50">
        {submitting ? 'Submitting…' : 'Submit Review'}
      </button>
    </form>
  );
}
