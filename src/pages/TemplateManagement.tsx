import React, { useEffect, useMemo, useState } from 'react';
import { Palette, Save, Plus, Trash2, Eye } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { TemplatedMenu } from '../components/menu/TemplatedMenu';
import { TemplatePreviewDialog } from '../components/menu/TemplatePreviewDialog';
import {
  useMenuTemplateDefinitions,
  useUpdateMenuTemplateDefinition,
  useCreateMenuTemplateDefinition,
  useDeleteMenuTemplateDefinition,
} from '../hooks/useApiData';
import { ApiError } from '../lib/api';
import { friendlyError } from '../lib/errors';
import {
  resolveTemplateClasses,
  normaliseStructure,
  imagesVisible,
  BACKGROUND_MODE_OPTIONS,
  ACCENT_TOKEN_OPTIONS,
  LAYOUT_OPTIONS,
  CARD_STYLE_OPTIONS,
  IMAGE_POSITION_OPTIONS,
  IMAGE_ASPECT_OPTIONS,
  FONT_FAMILY_OPTIONS,
  HEADER_ALIGNMENT_OPTIONS,
  type BackgroundMode,
  type AccentToken,
  type MenuTemplateDefinition,
  type TemplateStructure,
} from '../lib/menuTemplates';
import { SAMPLE_RESTAURANT_COMPACT } from '../constants/sampleMenuData';

const selectClasses =
  'h-11 w-full rounded-control border border-line bg-surface px-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

/**
 * friendlyError's 400/409 branches return a generic message, which would
 * bury the specific, already-safe-to-show reason a create/delete was
 * refused (duplicate key; still in use; last remaining template). Prefer
 * the backend's own message for this page's admin-only actions - it never
 * carries raw SQL/UUIDs (see MenuTemplateService), only hand-written copy.
 */
function templateActionError(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.message && !/^request failed/i.test(err.message)) {
    return err.message;
  }
  return friendlyError(err, fallback);
}

/** Uppercase, underscored, <=20 chars, starting with a letter - matches the backend's key pattern exactly. */
function deriveTemplateKey(displayName: string): string {
  const cleaned = displayName.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 20);
  if (!cleaned) return '';
  return /^[A-Z]/.test(cleaned) ? cleaned : `T_${cleaned}`.slice(0, 20);
}

// ---------------------------------------------------------------------------
// Shared editor pieces
// ---------------------------------------------------------------------------

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-label-m text-ink">{label}</label>
      {children}
      {hint && <p className="mt-1 text-label-s text-muted">{hint}</p>}
    </div>
  );
}

/**
 * The structural controls, defined once and used by BOTH the row editor and
 * the create modal.
 *
 * Deliberately not duplicated per form: the previous page had the colour
 * selects written out twice, which is survivable for two fields and would not
 * be for ten - the two copies would drift the first time an option was added
 * to only one of them.
 */
function StructureControls({
  value,
  onChange,
  idPrefix,
}: {
  value: TemplateStructure;
  onChange: (next: TemplateStructure) => void;
  idPrefix: string;
}) {
  const set = <K extends keyof TemplateStructure>(k: K, v: TemplateStructure[K]) =>
    onChange({ ...value, [k]: v });

  // Aspect ratio has no effect once images are off, so it is disabled rather
  // than left looking editable. imagesVisible() is the single predicate for
  // that - showImages and imagePosition can each turn images off.
  const withImages = imagesVisible(value);

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Field label="Layout" hint={LAYOUT_OPTIONS.find((o) => o.value === value.layoutStructure)?.hint}>
        <select
          aria-label="Layout"
          value={value.layoutStructure}
          onChange={(e) => set('layoutStructure', e.target.value as TemplateStructure['layoutStructure'])}
          className={selectClasses}
        >
          {LAYOUT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Card style">
        <select
          aria-label="Card style"
          value={value.itemCardStyle}
          onChange={(e) => set('itemCardStyle', e.target.value as TemplateStructure['itemCardStyle'])}
          className={selectClasses}
        >
          {CARD_STYLE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Dish images">
        <select
          aria-label="Dish images"
          value={value.imagePosition}
          onChange={(e) => {
            const next = e.target.value as TemplateStructure['imagePosition'];
            // Keep the toggle and the position consistent so the admin cannot
            // save a template that says "show images" and "position: none".
            onChange({ ...value, imagePosition: next, showImages: next !== 'NONE' });
          }}
          className={selectClasses}
        >
          {IMAGE_POSITION_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Image shape" hint={withImages ? undefined : 'Not used while images are off'}>
        <select
          aria-label="Image shape"
          value={value.imageAspectRatio}
          disabled={!withImages}
          onChange={(e) => set('imageAspectRatio', e.target.value as TemplateStructure['imageAspectRatio'])}
          className={`${selectClasses} disabled:opacity-50`}
        >
          {IMAGE_ASPECT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Typeface" hint={FONT_FAMILY_OPTIONS.find((o) => o.value === value.fontFamily)?.hint}>
        <select
          aria-label="Typeface"
          value={value.fontFamily}
          onChange={(e) => set('fontFamily', e.target.value as TemplateStructure['fontFamily'])}
          className={selectClasses}
        >
          {FONT_FAMILY_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Header">
        <select
          aria-label="Header alignment"
          value={value.headerAlignment}
          onChange={(e) => set('headerAlignment', e.target.value as TemplateStructure['headerAlignment'])}
          className={selectClasses}
        >
          {HEADER_ALIGNMENT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>

      <div className="sm:col-span-2">
        <label htmlFor={`${idPrefix}-cover`} className="flex min-h-11 items-center gap-2">
          <input
            id={`${idPrefix}-cover`}
            type="checkbox"
            checked={value.showCoverImage}
            onChange={(e) => set('showCoverImage', e.target.checked)}
            className="h-4 w-4 rounded-[4px] border-line-strong accent-[var(--color-brand-dark)]"
          />
          <span className="text-label-m text-ink">Show cover banner</span>
        </label>
        <p className="text-label-s text-muted">
          Visible in Sample preview. A live branch has no cover image yet, so this has no effect on a real menu.
        </p>
      </div>
    </div>
  );
}

/**
 * The in-form live preview: a real menu section drawn with the same component
 * the customer page uses, from the shared sample dataset.
 *
 * It is wrapped in a fixed-width box because the layout classes are
 * container-query based - the box width, not the browser, is what decides
 * whether a grid goes two-up. 420px is wide enough to show a 2-up grid while
 * still fitting the create modal.
 */
function TemplatePreview({
  def,
  name,
}: {
  def: Pick<MenuTemplateDefinition, 'backgroundMode' | 'accentToken'> & Partial<MenuTemplateDefinition>;
  name: string;
}) {
  const template = useMemo(() => resolveTemplateClasses(def), [def]);
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between">
        <span className="text-label-s uppercase text-muted">Live preview</span>
        <span className="text-label-s text-muted">Sample data</span>
      </div>
      <div className="overflow-hidden rounded-control border border-line" style={{ maxWidth: 420 }}>
        <div data-view="customer">
          <TemplatedMenu
            template={template}
            title={name || 'Untitled'}
            tagline={SAMPLE_RESTAURANT_COMPACT.tagline}
            coverImage={SAMPLE_RESTAURANT_COMPACT.coverImage}
            categories={SAMPLE_RESTAURANT_COMPACT.categories}
            currency={SAMPLE_RESTAURANT_COMPACT.currency}
            showBadges
          />
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Row editor
// ---------------------------------------------------------------------------

function TemplateRow({ template, canDelete }: { template: MenuTemplateDefinition; canDelete: boolean }) {
  const updateMutation = useUpdateMenuTemplateDefinition();
  const deleteMutation = useDeleteMenuTemplateDefinition();
  const [displayName, setDisplayName] = useState(template.displayName);
  const [backgroundMode, setBackgroundMode] = useState<BackgroundMode>(template.backgroundMode);
  const [accentToken, setAccentToken] = useState<AccentToken>(template.accentToken);
  const [structure, setStructure] = useState<TemplateStructure>(() => normaliseStructure(template));
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  // The definition can change under us (another admin, or our own previous
  // save) - resync local edit state rather than let it go stale.
  useEffect(() => {
    setDisplayName(template.displayName);
    setBackgroundMode(template.backgroundMode);
    setAccentToken(template.accentToken);
    setStructure(normaliseStructure(template));
  }, [template]);

  const savedStructure = useMemo(() => normaliseStructure(template), [template]);

  const dirty =
    displayName !== template.displayName ||
    backgroundMode !== template.backgroundMode ||
    accentToken !== template.accentToken ||
    // A shallow compare over the eight structural keys. Every value is a
    // string or boolean, so this is exact - no need for a deep compare.
    (Object.keys(structure) as (keyof TemplateStructure)[]).some((k) => structure[k] !== savedStructure[k]);

  const draft: MenuTemplateDefinition = { ...template, displayName, backgroundMode, accentToken, ...structure };
  const swatch = resolveTemplateClasses(draft).swatch;

  const handleSave = async () => {
    setError(null);
    setSaved(false);
    try {
      await updateMutation.mutateAsync({
        key: template.key,
        data: { displayName, backgroundMode, accentToken, ...structure },
      });
      setSaved(true);
    } catch (err) {
      setError(templateActionError(err, 'We could not save this template.'));
    }
  };

  const handleDelete = async () => {
    if (!confirm(`Delete "${template.displayName}"? This cannot be undone.`)) return;
    setError(null);
    try {
      await deleteMutation.mutateAsync(template.key);
    } catch (err) {
      setError(templateActionError(err, 'We could not delete this template.'));
    }
  };

  return (
    <Card className="space-y-4">
      <div className="flex items-center gap-2">
        <span className={swatch} aria-hidden="true" />
        <span className="text-title-s text-ink">{template.displayName}</span>
        <code className="rounded-control bg-surface-2 px-2 py-0.5 text-label-s text-muted">{template.key}</code>
        <Button
          variant="secondary"
          onClick={() => setPreviewOpen(true)}
          className="ml-auto shrink-0"
          aria-label={`Preview ${template.displayName}`}
        >
          <Eye className="h-4 w-4" aria-hidden="true" />
          Preview
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <FormField
          label="Display Name"
          required
          maxLength={60}
          value={displayName}
          onChange={(e) => {
            setDisplayName(e.target.value);
            setSaved(false);
          }}
        />
        <Field label="Background">
          <select
            aria-label="Background"
            value={backgroundMode}
            onChange={(e) => {
              setBackgroundMode(e.target.value as BackgroundMode);
              setSaved(false);
            }}
            className={selectClasses}
          >
            {BACKGROUND_MODE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Accent">
          <select
            aria-label="Accent"
            value={accentToken}
            onChange={(e) => {
              setAccentToken(e.target.value as AccentToken);
              setSaved(false);
            }}
            className={selectClasses}
          >
            {ACCENT_TOKEN_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </Field>
      </div>

      <StructureControls
        value={structure}
        onChange={(next) => {
          setStructure(next);
          setSaved(false);
        }}
        idPrefix={`row-${template.key}`}
      />

      <div className="flex flex-wrap gap-2">
        <Button onClick={handleSave} loading={updateMutation.isPending} disabled={!dirty}>
          <Save className="h-4 w-4" aria-hidden="true" />
          Save
        </Button>
        <Button
          variant="destructive"
          onClick={handleDelete}
          loading={deleteMutation.isPending}
          disabled={!canDelete}
          title={canDelete ? undefined : 'At least one template must remain'}
          aria-label={`Delete ${template.displayName}`}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>

      <TemplatePreview def={draft} name={displayName || template.key} />

      {error && (
        <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
          {error}
        </div>
      )}
      {saved && !dirty && (
        <div role="status" className="rounded-control bg-success-soft px-3 py-3 text-label-s text-ink">
          Saved. Every branch using "{template.key}" now shows this look.
        </div>
      )}

      {/* Previews the DRAFT, not the saved row - an admin should be able to
          inspect an unsaved change full-screen before committing it. */}
      <TemplatePreviewDialog open={previewOpen} onClose={() => setPreviewOpen(false)} definition={draft} />
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------

function NewTemplateModal({
  open,
  onClose,
  existingKeys,
}: {
  open: boolean;
  onClose: () => void;
  existingKeys: string[];
}) {
  const createMutation = useCreateMenuTemplateDefinition();
  const [displayName, setDisplayName] = useState('');
  const [backgroundMode, setBackgroundMode] = useState<BackgroundMode>('LIGHT');
  const [accentToken, setAccentToken] = useState<AccentToken>('BRAND');
  const [structure, setStructure] = useState<TemplateStructure>(() => normaliseStructure({}));
  const [error, setError] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const key = deriveTemplateKey(displayName);
  const keyTaken = key.length > 0 && existingKeys.includes(key);

  const reset = () => {
    setDisplayName('');
    setBackgroundMode('LIGHT');
    setAccentToken('BRAND');
    setStructure(normaliseStructure({}));
    setError(null);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleCreate = async () => {
    setError(null);
    if (!key) {
      setError('Enter a name that includes at least one letter or number.');
      return;
    }
    if (keyTaken) {
      setError(`A template with identifier "${key}" already exists. Try a different name.`);
      return;
    }
    try {
      await createMutation.mutateAsync({
        key,
        displayName: displayName.trim(),
        backgroundMode,
        accentToken,
        ...structure,
      });
      handleClose();
    } catch (err) {
      setError(templateActionError(err, 'We could not create this template.'));
    }
  };

  const draft: MenuTemplateDefinition = {
    key: key || 'NEW',
    displayName,
    backgroundMode,
    accentToken,
    ...structure,
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="New Template"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button variant="secondary" onClick={() => setPreviewOpen(true)} disabled={!displayName.trim()}>
            <Eye className="h-4 w-4" aria-hidden="true" />
            Full preview
          </Button>
          <Button onClick={handleCreate} loading={createMutation.isPending} disabled={!displayName.trim()}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <FormField
          label="Display Name"
          required
          maxLength={60}
          placeholder="e.g. Rustic Bistro"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          hint={key ? `Identifier: ${key}` : undefined}
        />

        <div className="grid grid-cols-2 gap-4">
          <Field label="Background">
            <select
              aria-label="Background"
              value={backgroundMode}
              onChange={(e) => setBackgroundMode(e.target.value as BackgroundMode)}
              className={selectClasses}
            >
              {BACKGROUND_MODE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Accent">
            <select
              aria-label="Accent"
              value={accentToken}
              onChange={(e) => setAccentToken(e.target.value as AccentToken)}
              className={selectClasses}
            >
              {ACCENT_TOKEN_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <StructureControls value={structure} onChange={setStructure} idPrefix="new" />

        <TemplatePreview def={draft} name={displayName} />

        {error && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            {error}
          </div>
        )}
      </div>

      <TemplatePreviewDialog open={previewOpen} onClose={() => setPreviewOpen(false)} definition={draft} />
    </Modal>
  );
}

// ---------------------------------------------------------------------------

export const TemplateManagement: React.FC = () => {
  const { data: templates, isLoading, error, refetch } = useMenuTemplateDefinitions();
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <DashboardLayout title="Templates">
      {/* Wider than the §5.4 forms cap because each row now carries a rendered
          preview beside its controls, not just text inputs. */}
      <div className="mx-auto max-w-[60rem] space-y-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Palette className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Templates
            </h2>
            <p className="mt-1 text-body-m text-muted">
              Create and edit the curated digital-menu templates merchants choose from in Menu Builder. Changes apply
              immediately to every branch already using that template. Use Preview to see a template against a real
              branch&apos;s menu before you save.
            </p>
          </div>
          <Button onClick={() => setModalOpen(true)} className="shrink-0">
            <Plus className="h-4 w-4" aria-hidden="true" />
            New Template
          </Button>
        </div>

        {isLoading && <Spinner label="Loading templates..." />}
        {!isLoading && error && (
          <ErrorState
            message={friendlyError(error, 'We could not load the template definitions.')}
            onRetry={() => refetch()}
          />
        )}

        {!isLoading &&
          !error &&
          (templates ?? []).map((template) => (
            <TemplateRow key={template.key} template={template} canDelete={(templates ?? []).length > 1} />
          ))}
      </div>

      <NewTemplateModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        existingKeys={(templates ?? []).map((t) => t.key)}
      />
    </DashboardLayout>
  );
};
