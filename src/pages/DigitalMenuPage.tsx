import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Star } from 'lucide-react';
import {
  resolveBranch,
  resolvePrimaryBranch,
  fetchBranchMenu,
  fetchReviewSummary,
  submitReview,
  type DigitalMenuResolution,
} from '../lib/digitalMenu';
import { resolveMediaUrl } from '../lib/api';
import { Spinner, ErrorState } from '../components/ui/States';
import { Button } from '../components/ui/Button';
import { TemplatedMenu } from '../components/menu/TemplatedMenu';
import { useMenuTemplateDefinitions } from '../hooks/useApiData';
import { resolveTemplateClasses, type ResolvedTemplateClasses } from '../lib/menuTemplates';

/**
 * A small, team-curated, fixed set of visual presentations — the merchant
 * picks one (MenuBuilderPage), not a merchant-authored/customizable
 * template system. DESIGN.md §1 rule 3 is why this page is allowed to be
 * expressive at all — it's the one context in the product that is. Each
 * template's actual colors come from the admin-editable definitions
 * (Templates page, ../lib/menuTemplates.ts resolveTemplateClasses) rather
 * than being hardcoded here, so an admin edit changes what customers see
 * without a deploy. CLASSIC/MODERN_DARK/VIBRANT below is only the
 * fallback used before the definitions have loaded.
 */
const FALLBACK_TEMPLATE = resolveTemplateClasses({ backgroundMode: 'LIGHT', accentToken: 'BRAND' });

/**
 * Read-only digital menu view — no cart, no ordering (out of scope for the
 * phase-1 HLD rollout; see docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md).
 * Deliberately a new page, not a reuse of CustomerMenuPage, which carries
 * cart/order UI that belongs to the parked ordering flow.
 */
export function DigitalMenuPage() {
  const { merchantSlug, branchSlug } = useParams<{ merchantSlug: string; branchSlug?: string }>();

  if (!merchantSlug) {
    return <CenteredMessage>Menu not found.</CenteredMessage>;
  }

  if (!branchSlug) {
    return <PrimaryBranchRedirect merchantSlug={merchantSlug} />;
  }

  return <BranchMenu merchantSlug={merchantSlug} branchSlug={branchSlug} />;
}

/** Frame A (DESIGN.md §5.4): canvas ground, 640 max-width, 16 gutter. */
function CenteredMessage({ children, isError = true }: { children: React.ReactNode; isError?: boolean }) {
  return (
    <div data-view="customer" className="flex min-h-screen items-center justify-center bg-canvas px-4">
      <div className="mx-auto w-full max-w-[40rem]" role={isError ? 'alert' : undefined}>
        <p className="text-center text-body-l text-muted">{children}</p>
      </div>
    </div>
  );
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
    return (
      <div data-view="customer" className="flex min-h-screen items-center justify-center bg-canvas">
        <Spinner label="Loading menu…" />
      </div>
    );
  }
  if (resolution === 'not-found') {
    return <CenteredMessage>Menu coming soon.</CenteredMessage>;
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

  const templatesQuery = useMenuTemplateDefinitions();

  if (resolutionQuery.isError) {
    return <CenteredMessage>Menu not found.</CenteredMessage>;
  }
  if (resolutionQuery.isLoading || menuQuery.isLoading) {
    return (
      <div data-view="customer" className="flex min-h-screen items-center justify-center bg-canvas">
        <Spinner label="Loading menu…" />
      </div>
    );
  }
  if (menuQuery.isError) {
    return <CenteredMessage>Menu coming soon.</CenteredMessage>;
  }

  const templateStyle = menuQuery.data?.templateStyle ?? 'CLASSIC';
  const templateDef = templatesQuery.data?.find((t) => t.key === templateStyle);
  const template: ResolvedTemplateClasses = templateDef
    ? resolveTemplateClasses(templateDef)
    : FALLBACK_TEMPLATE;
  const branchId = resolutionQuery.data?.branchId;

  /**
   * The template's structural controls (layout, card style, image position and
   * ratio, face, header alignment) all arrive through `template` and are
   * applied by TemplatedMenu - the same component the admin preview renders,
   * so what an admin approves is literally what a guest gets.
   *
   * HEADER ARTWORK is all optional and all currently unset in production:
   * coverImageUrl and tagline are new columns nobody has filled in, and no
   * merchant has uploaded a logoUrl either. That is fine by design - when the
   * cover is absent TemplatedMenu borrows the branch's first dish photo, so a
   * real menu still opens on a banner rather than a bare heading, and the logo
   * and tagline are simply omitted. As merchants add artwork the header gets
   * better with no further code change.
   */
  const resolution = resolutionQuery.data;

  return (
    <div data-view="customer" className={`min-h-screen ${template.page}`}>
      <div className="mx-auto max-w-[40rem]">
        <TemplatedMenu
          template={template}
          title={resolution?.branchName ?? ''}
          tagline={resolution?.tagline}
          coverImage={resolution?.coverImageUrl ? resolveMediaUrl(resolution.coverImageUrl) : undefined}
          logoImage={resolution?.logoUrl ? resolveMediaUrl(resolution.logoUrl) : undefined}
          categories={menuQuery.data?.categories ?? []}
          resolveImageUrl={resolveMediaUrl}
          // Badges are sample-only; a live menu has no badge data (see
          // src/constants/sampleMenuData.ts).
          showBadges={false}
          // The slot is handed the right secondary-text class for whichever
          // ground the header ended up on — page ground, or the dark scrim over
          // a cover photo. Passing template.description here would have put
          // grey text on the scrim.
          headerSlot={
            branchId != null
              ? (secondaryTextClass) => <RatingBadge branchId={branchId} textClass={secondaryTextClass} />
              : undefined
          }
          footerSlot={branchId != null ? <ReviewForm branchId={branchId} /> : undefined}
        />
      </div>
    </div>
  );
}

function RatingBadge({ branchId, textClass }: { branchId: number; textClass: string }) {
  const summaryQuery = useQuery({
    queryKey: ['digital-menu-review-summary', branchId],
    queryFn: () => fetchReviewSummary(branchId),
  });

  if (!summaryQuery.data || summaryQuery.data.count === 0) return null;
  return (
    <p className={`mt-1 flex items-center justify-center gap-1 text-label-m ${textClass}`}>
      <Star className="h-3.5 w-3.5 fill-current" aria-hidden="true" />
      {summaryQuery.data.averageRating.toFixed(1)} · {summaryQuery.data.count} review{summaryQuery.data.count === 1 ? '' : 's'}
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
      <div className="px-4 py-8 text-center text-body-m opacity-70">Thanks for your feedback!</div>
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
    <form onSubmit={handleSubmit} className="space-y-3 px-4 py-8">
      <h2 className="text-title-s">Rate your visit</h2>
      {/* 44x44 minimum in the customer context (§5.7) — thumb, one-handed. */}
      <div className="flex gap-1" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={rating === n}
            aria-label={`${n} star${n === 1 ? '' : 's'}`}
            onClick={() => setRating(n)}
            className="flex h-11 w-11 items-center justify-center"
          >
            <Star className="h-6 w-6 fill-current" style={{ opacity: n <= rating ? 1 : 0.3 }} aria-hidden="true" />
          </button>
        ))}
      </div>
      <label className="block">
        <span className="sr-only">Comment (optional)</span>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="Tell us about your visit (optional)"
          rows={3}
          className="w-full rounded-control border border-current/20 bg-transparent p-3 text-body-m focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
        />
      </label>
      {/* A self-contained light chip, not bare text: template.page can be a
          dark ground (MODERN_DARK), and --color-danger has no documented
          dark-ground pairing — a light danger-soft chip stays safe either way. */}
      {error && <p role="alert" className="inline-block rounded-control bg-danger-soft px-3 py-2 text-label-s text-ink">{error}</p>}
      <Button type="submit" loading={submitting} variant="primary">
        {submitting ? 'Submitting…' : 'Submit Review'}
      </Button>
    </form>
  );
}
