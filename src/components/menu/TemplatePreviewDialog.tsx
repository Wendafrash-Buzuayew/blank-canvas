import React, { useMemo, useState } from 'react';
import { Monitor, Smartphone, Info } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Spinner, ErrorState } from '../ui/States';
import { TemplatedMenu, type TemplatedMenuCategory } from './TemplatedMenu';
import { resolveTemplateClasses, type MenuTemplateDefinition } from '../../lib/menuTemplates';
import { SAMPLE_RESTAURANT } from '../../constants/sampleMenuData';
import { useBranchMenu } from '../../hooks/useApiData';
import { useBranchesLookup } from '../../hooks/useLookups';
import { resolveMediaUrl } from '../../lib/api';
import { friendlyError } from '../../lib/errors';

/**
 * The admin's full-screen template inspector.
 *
 * Two things make this trustworthy rather than decorative:
 *  - it renders TemplatedMenu, the same component the live customer page
 *    renders, so there is no second implementation to drift; and
 *  - the device toggle actually changes the container width, and the layout
 *    classes are container-query based, so a 375px frame really does produce
 *    the phone layout (see LAYOUT_CONTAINER in src/lib/menuTemplates.ts).
 */

type PreviewSource = 'SAMPLE' | 'MERCHANT';
type PreviewDevice = 'MOBILE' | 'DESKTOP';

export interface TemplatePreviewDialogProps {
  open: boolean;
  onClose: () => void;
  /** The definition to preview — the live edit state, not necessarily what is saved. */
  definition: MenuTemplateDefinition;
}

const MOBILE_WIDTH = 375;

/** Frame A's real content cap (DESIGN.md §5.4) — 40rem. */
const DESKTOP_WIDTH = 640;

export function TemplatePreviewDialog({ open, onClose, definition }: TemplatePreviewDialogProps) {
  const [source, setSource] = useState<PreviewSource>('SAMPLE');
  const [device, setDevice] = useState<PreviewDevice>('MOBILE');
  const [branchId, setBranchId] = useState<number | null>(null);

  const template = useMemo(() => resolveTemplateClasses(definition), [definition]);

  const branchesQuery = useBranchesLookup();
  const branches = branchesQuery.data ?? [];
  const effectiveBranchId = branchId ?? branches[0]?.id ?? null;
  const selectedBranch = branches.find((b) => b.id === effectiveBranchId);

  // Only fetched in merchant mode, so opening the dialog in sample mode costs
  // no request.
  const menuQuery = useBranchMenu(source === 'MERCHANT' && effectiveBranchId != null ? effectiveBranchId : undefined);

  const frameWidth = device === 'MOBILE' ? MOBILE_WIDTH : DESKTOP_WIDTH;

  const sampleCategories: readonly TemplatedMenuCategory[] = SAMPLE_RESTAURANT.categories;
  const merchantCategories: readonly TemplatedMenuCategory[] = menuQuery.data?.categories ?? [];

  return (
    <Modal
      open={open}
      onClose={onClose}
      presentation="fullscreen"
      title={`Preview · ${definition.displayName || definition.key}`}
      bodyClassName="flex min-h-0 flex-1 flex-col"
    >
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 border-b border-line px-4 py-3">
        <div role="group" aria-label="Preview data" className="flex rounded-control border border-line-strong p-0.5">
          <ToggleButton active={source === 'SAMPLE'} onClick={() => setSource('SAMPLE')}>
            Sample menu
          </ToggleButton>
          <ToggleButton active={source === 'MERCHANT'} onClick={() => setSource('MERCHANT')}>
            Merchant live menu
          </ToggleButton>
        </div>

        {source === 'MERCHANT' && (
          <label className="flex items-center gap-2">
            <span className="text-label-m text-ink">Branch</span>
            <select
              value={effectiveBranchId ?? ''}
              onChange={(e) => setBranchId(Number(e.target.value))}
              className="h-11 rounded-control border border-line bg-surface px-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
            >
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </label>
        )}

        <div role="group" aria-label="Device" className="ml-auto flex rounded-control border border-line-strong p-0.5">
          <ToggleButton active={device === 'MOBILE'} onClick={() => setDevice('MOBILE')}>
            <Smartphone className="h-4 w-4" aria-hidden="true" />
            Mobile · 375
          </ToggleButton>
          <ToggleButton active={device === 'DESKTOP'} onClick={() => setDevice('DESKTOP')}>
            <Monitor className="h-4 w-4" aria-hidden="true" />
            Desktop · 640
          </ToggleButton>
        </div>
      </div>

      {/* Honesty notes. Both describe a real difference between what this
          preview can show and what a guest gets, and both would otherwise
          mislead an admin into picking a template for a feature that is not
          there. */}
      <div className="flex flex-col gap-1 border-b border-line bg-info-soft px-4 py-2">
        {source === 'SAMPLE' && (
          <p className="flex items-start gap-2 text-label-s text-ink">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            Sample data. The <strong>Popular</strong> / <strong>Chef Choice</strong> badges and the cover banner
            illustrate the layout only — a live menu has no badge or cover-image data yet.
          </p>
        )}
        {source === 'MERCHANT' && (
          <p className="flex items-start gap-2 text-label-s text-ink">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            {selectedBranch ? selectedBranch.name : 'This branch'}&apos;s real menu, drawn in{' '}
            <strong>{definition.displayName || definition.key}</strong> — which is not necessarily the template the
            branch is currently using. Nothing here is saved.
          </p>
        )}
      </div>

      {/* Stage. The grey ground makes the frame edge visible at mobile width. */}
      <div className="min-h-0 flex-1 overflow-y-auto bg-surface-2 p-4">
        <div
          className="mx-auto overflow-hidden rounded-card border border-line-strong bg-surface"
          style={{ width: frameWidth, maxWidth: '100%' }}
        >
          {/* data-view="customer" so the preview inherits the same customer-context
              type scale and accent scoping as the real page (DESIGN.md §1 rule 3). */}
          <div data-view="customer">
            {source === 'SAMPLE' ? (
              <TemplatedMenu
                template={template}
                title={SAMPLE_RESTAURANT.name}
                tagline={SAMPLE_RESTAURANT.tagline}
                coverImage={SAMPLE_RESTAURANT.coverImage}
                logoImage={SAMPLE_RESTAURANT.logoImage}
                categories={sampleCategories}
                currency={SAMPLE_RESTAURANT.currency}
                showBadges
              />
            ) : menuQuery.isLoading ? (
              <div className="p-8">
                <Spinner label="Loading branch menu…" />
              </div>
            ) : menuQuery.isError ? (
              <div className="p-4">
                <ErrorState
                  message={friendlyError(menuQuery.error, 'We could not load this branch’s menu.')}
                  onRetry={() => menuQuery.refetch()}
                />
              </div>
            ) : merchantCategories.length === 0 ? (
              <div className="p-8 text-center text-body-m text-muted">
                {selectedBranch ? `${selectedBranch.name} has no menu items yet.` : 'No branch selected.'}
              </div>
            ) : (
              <TemplatedMenu
                template={template}
                title={selectedBranch?.name ?? 'Branch'}
                categories={merchantCategories}
                resolveImageUrl={resolveMediaUrl}
                showBadges={false}
              />
            )}
          </div>
        </div>
      </div>
    </Modal>
  );
}

/**
 * A segmented-control button. Active state shifts weight as well as colour, so
 * colour is not its only carrier (DESIGN.md §7.3), and `aria-pressed` makes the
 * state available without sight.
 */
function ToggleButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`flex min-h-9 items-center gap-2 rounded-control px-3 text-label-m transition-colors ${
        active ? 'bg-brand-dark font-semibold text-brand-fg' : 'font-medium text-muted hover:bg-surface-2 hover:text-ink'
      }`}
    >
      {children}
    </button>
  );
}
