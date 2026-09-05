import React, { useEffect, useState } from 'react';
import { Palette, Save, Plus, Trash2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
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
  BACKGROUND_MODE_OPTIONS,
  ACCENT_TOKEN_OPTIONS,
  type BackgroundMode,
  type AccentToken,
  type MenuTemplateDefinition,
} from '../lib/menuTemplates';

const selectClasses = 'h-11 w-full rounded-control border border-line bg-surface px-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

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

function TemplatePreview({ def, name }: { def: Pick<MenuTemplateDefinition, 'backgroundMode' | 'accentToken'>; name: string }) {
  const preview = resolveTemplateClasses(def);
  return (
    <div className={`overflow-hidden rounded-control border border-line ${preview.page}`}>
      <div className={preview.header}>
        <h3 className={preview.title}>{name || 'Untitled'}</h3>
      </div>
      <div className="px-4 pb-4">
        <p className={preview.categoryHeading}>Starters</p>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className={preview.itemName}>Sample Dish</div>
            <p className={preview.description}>A short description of the dish goes here.</p>
          </div>
          <div className={preview.priceWrap}>
            <div className={preview.price}>120 ETB</div>
          </div>
        </div>
      </div>
    </div>
  );
}

function TemplateRow({ template, canDelete }: { template: MenuTemplateDefinition; canDelete: boolean }) {
  const updateMutation = useUpdateMenuTemplateDefinition();
  const deleteMutation = useDeleteMenuTemplateDefinition();
  const [displayName, setDisplayName] = useState(template.displayName);
  const [backgroundMode, setBackgroundMode] = useState<BackgroundMode>(template.backgroundMode);
  const [accentToken, setAccentToken] = useState<AccentToken>(template.accentToken);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  // The definition can change under us (another admin, or our own previous
  // save) - resync local edit state rather than let it go stale.
  useEffect(() => {
    setDisplayName(template.displayName);
    setBackgroundMode(template.backgroundMode);
    setAccentToken(template.accentToken);
  }, [template]);

  const dirty =
    displayName !== template.displayName ||
    backgroundMode !== template.backgroundMode ||
    accentToken !== template.accentToken;

  const swatch = resolveTemplateClasses({ backgroundMode, accentToken }).swatch;

  const handleSave = async () => {
    setError(null);
    setSaved(false);
    try {
      await updateMutation.mutateAsync({ key: template.key, data: { displayName, backgroundMode, accentToken } });
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
        <code className="ml-auto rounded-control bg-surface-2 px-2 py-0.5 text-label-s text-muted">{template.key}</code>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_1fr_1fr_auto_auto]">
        <FormField
          label="Display Name"
          required
          maxLength={60}
          value={displayName}
          onChange={(e) => { setDisplayName(e.target.value); setSaved(false); }}
        />
        <div>
          <label className="mb-1 block text-label-m text-ink">Background</label>
          <select
            value={backgroundMode}
            onChange={(e) => { setBackgroundMode(e.target.value as BackgroundMode); setSaved(false); }}
            className={selectClasses}
          >
            {BACKGROUND_MODE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-label-m text-ink">Accent</label>
          <select
            value={accentToken}
            onChange={(e) => { setAccentToken(e.target.value as AccentToken); setSaved(false); }}
            className={selectClasses}
          >
            {ACCENT_TOKEN_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
        </div>
        <div className="flex items-end">
          <Button onClick={handleSave} loading={updateMutation.isPending} disabled={!dirty} fullWidth>
            <Save className="h-4 w-4" aria-hidden="true" />
            Save
          </Button>
        </div>
        <div className="flex items-end">
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
      </div>

      <TemplatePreview def={{ backgroundMode, accentToken }} name={displayName || template.key} />

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
    </Card>
  );
}

function NewTemplateModal({ open, onClose, existingKeys }: { open: boolean; onClose: () => void; existingKeys: string[] }) {
  const createMutation = useCreateMenuTemplateDefinition();
  const [displayName, setDisplayName] = useState('');
  const [backgroundMode, setBackgroundMode] = useState<BackgroundMode>('LIGHT');
  const [accentToken, setAccentToken] = useState<AccentToken>('BRAND');
  const [error, setError] = useState<string | null>(null);

  const key = deriveTemplateKey(displayName);
  const keyTaken = key.length > 0 && existingKeys.includes(key);

  const reset = () => {
    setDisplayName('');
    setBackgroundMode('LIGHT');
    setAccentToken('BRAND');
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
      await createMutation.mutateAsync({ key, displayName: displayName.trim(), backgroundMode, accentToken });
      handleClose();
    } catch (err) {
      setError(templateActionError(err, 'We could not create this template.'));
    }
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="New Template"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={handleClose}>Cancel</Button>
          <Button onClick={handleCreate} loading={createMutation.isPending} disabled={!displayName.trim()}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div>
          <FormField
            label="Display Name"
            required
            maxLength={60}
            placeholder="e.g. Rustic Bistro"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            hint={key ? `Identifier: ${key}` : undefined}
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="mb-1 block text-label-m text-ink">Background</label>
            <select value={backgroundMode} onChange={(e) => setBackgroundMode(e.target.value as BackgroundMode)} className={selectClasses}>
              {BACKGROUND_MODE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-label-m text-ink">Accent</label>
            <select value={accentToken} onChange={(e) => setAccentToken(e.target.value as AccentToken)} className={selectClasses}>
              {ACCENT_TOKEN_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
            </select>
          </div>
        </div>

        <TemplatePreview def={{ backgroundMode, accentToken }} name={displayName} />

        {error && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            {error}
          </div>
        )}
      </div>
    </Modal>
  );
}

export const TemplateManagement: React.FC = () => {
  const { data: templates, isLoading, error, refetch } = useMenuTemplateDefinitions();
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <DashboardLayout title="Templates">
      {/* DESIGN.md 5.4: forms/settings are the 720-max-width case in frame C. */}
      <div className="mx-auto max-w-[45rem] space-y-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Palette className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Templates
            </h2>
            <p className="mt-1 text-body-m text-muted">
              Create and edit the curated digital-menu templates merchants choose from in Menu Builder.
              Changes apply immediately to every branch already using that template.
            </p>
          </div>
          <Button onClick={() => setModalOpen(true)} className="shrink-0">
            <Plus className="h-4 w-4" aria-hidden="true" />
            New Template
          </Button>
        </div>

        {isLoading && <Spinner label="Loading templates..." />}
        {!isLoading && error && (
          <ErrorState message={friendlyError(error, 'We could not load the template definitions.')} onRetry={() => refetch()} />
        )}

        {!isLoading && !error && (templates ?? []).map((template) => (
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
