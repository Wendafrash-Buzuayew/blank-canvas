import React, { useEffect, useState } from 'react';
import { Palette, Save } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { FormField } from '../components/ui/FormField';
import { useMenuTemplateDefinitions, useUpdateMenuTemplateDefinition } from '../hooks/useApiData';
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

function TemplateRow({ template }: { template: MenuTemplateDefinition }) {
  const updateMutation = useUpdateMenuTemplateDefinition();
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

  const preview = resolveTemplateClasses({ backgroundMode, accentToken });

  const handleSave = async () => {
    setError(null);
    setSaved(false);
    try {
      await updateMutation.mutateAsync({ key: template.key, data: { displayName, backgroundMode, accentToken } });
      setSaved(true);
    } catch (err) {
      setError(friendlyError(err, 'We could not save this template.'));
    }
  };

  return (
    <Card className="space-y-4">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_1fr_1fr_auto]">
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
      </div>

      {/* Live preview - the same class-resolution the customer digital menu itself uses. */}
      <div className={`overflow-hidden rounded-control border border-line ${preview.page}`}>
        <div className={preview.header}>
          <h3 className={preview.title}>{displayName || template.key}</h3>
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

export const TemplateManagement: React.FC = () => {
  const { data: templates, isLoading, error, refetch } = useMenuTemplateDefinitions();

  return (
    <DashboardLayout title="Templates">
      {/* DESIGN.md 5.4: forms/settings are the 720-max-width case in frame C. */}
      <div className="mx-auto max-w-[45rem] space-y-6">
        <div>
          <h2 className="flex items-center gap-2 text-title-m text-ink">
            <Palette className="h-5 w-5 text-brand-press" aria-hidden="true" />
            Templates
          </h2>
          <p className="mt-1 text-body-m text-muted">
            Edit the look of the three curated digital-menu templates merchants choose from in Menu Builder.
            Changes apply immediately to every branch already using that template.
          </p>
        </div>

        {isLoading && <Spinner label="Loading templates..." />}
        {!isLoading && error && (
          <ErrorState message={friendlyError(error, 'We could not load the template definitions.')} onRetry={() => refetch()} />
        )}

        {!isLoading && !error && (templates ?? []).map((template) => (
          <TemplateRow key={template.key} template={template} />
        ))}
      </div>
    </DashboardLayout>
  );
};
