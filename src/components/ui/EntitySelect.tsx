import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Loader2, Search, X } from 'lucide-react';

export interface EntitySelectProps<T> {
  label?: string;
  placeholder?: string;
  value: string | number | null | undefined;
  onChange: (value: string | null, option: T | null) => void;
  options: T[];
  labelKey?: keyof T | ((option: T) => string);
  valueKey?: keyof T;
  descriptionKey?: keyof T | ((option: T) => string | undefined);
  isLoading?: boolean;
  error?: string | null;
  disabled?: boolean;
  required?: boolean;
  clearable?: boolean;
  emptyMessage?: string;
  emptyAction?: React.ReactNode;
  loadingMessage?: string;
  helperText?: string;
  searchThreshold?: number;
}

/**
 * Generic searchable entity selector.
 * Displays a human-readable label while storing the entity identifier (UUID / numeric id).
 * Supports search, loading, empty, disabled and error states plus keyboard navigation.
 */
export function EntitySelect<T extends Record<string, any>>({
  label,
  placeholder = 'Select...',
  value,
  onChange,
  options,
  labelKey = 'name' as keyof T,
  valueKey = 'id' as keyof T,
  descriptionKey,
  isLoading = false,
  error = null,
  disabled = false,
  required = false,
  clearable = false,
  emptyMessage = 'No options available.',
  emptyAction,
  loadingMessage = 'Loading...',
  helperText,
  searchThreshold = 6,
}: EntitySelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlight, setHighlight] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const getLabel = (option: T): string =>
    typeof labelKey === 'function' ? labelKey(option) : String(option[labelKey] ?? '');
  const getDescription = (option: T): string | undefined => {
    if (!descriptionKey) return undefined;
    return typeof descriptionKey === 'function'
      ? descriptionKey(option)
      : (option[descriptionKey] as string | undefined);
  };
  const getValue = (option: T): string => String(option[valueKey]);

  const selected = useMemo(
    () => options.find((o) => value != null && value !== '' && getValue(o) === String(value)) ?? null,
    [options, value]
  );

  const filtered = useMemo(() => {
    if (!query.trim()) return options;
    const q = query.trim().toLowerCase();
    return options.filter((o) => {
      const desc = getDescription(o) || '';
      return getLabel(o).toLowerCase().includes(q) || desc.toLowerCase().includes(q);
    });
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    const onClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery('');
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, [open]);

  useEffect(() => {
    if (open && options.length >= searchThreshold) {
      searchRef.current?.focus();
    }
    if (!open) setHighlight(0);
  }, [open, options.length, searchThreshold]);

  const commit = (option: T | null) => {
    onChange(option ? getValue(option) : null, option);
    setOpen(false);
    setQuery('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;
    if (e.key === 'Enter' || e.key === ' ') {
      if (!open) {
        e.preventDefault();
        setOpen(true);
        return;
      }
      if (filtered[highlight]) {
        e.preventDefault();
        commit(filtered[highlight]);
      }
      return;
    }
    if (e.key === 'Escape') {
      setOpen(false);
      setQuery('');
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) setOpen(true);
      setHighlight((h) => Math.min(h + 1, Math.max(filtered.length - 1, 0)));
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    }
  };

  const isDisabled = disabled || isLoading || !!error;
  const showSearch = options.length >= searchThreshold;

  return (
    <div className="w-full" ref={containerRef}>
      {label && (
        <label className="mb-1 block text-label-m text-ink">
          {label}
          {required && <span className="ml-0.5 text-danger">*</span>}
        </label>
      )}

      <div className="relative">
        <button
          type="button"
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          disabled={isDisabled}
          onClick={() => setOpen((o) => !o)}
          onKeyDown={handleKeyDown}
          className={`flex h-11 w-full items-center justify-between gap-2 rounded-control border px-3 text-left text-body-m transition-colors ${
            error
              ? 'border-danger bg-danger-soft text-ink'
              : isDisabled
              ? 'cursor-not-allowed border-line bg-surface-2 text-muted'
              : 'border-line bg-surface text-ink hover:border-line-strong focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark'
          }`}
        >
          <span className="flex items-center gap-2 truncate">
            {isLoading && <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" aria-hidden="true" />}
            {isLoading
              ? loadingMessage
              : error
              ? error
              : selected
              ? getLabel(selected)
              : <span className="text-muted">{placeholder}</span>}
          </span>
          <span className="flex shrink-0 items-center gap-1">
            {clearable && selected && !isDisabled && (
              <span
                role="button"
                tabIndex={-1}
                aria-label="Clear selection"
                onClick={(e) => {
                  e.stopPropagation();
                  commit(null);
                }}
                className="rounded-control p-0.5 text-muted hover:text-ink"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </span>
            )}
            <ChevronDown className={`h-4 w-4 text-muted transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
          </span>
        </button>

        {open && !isDisabled && (
          // z-50, not z-modal — see the comment in Modal.tsx: that name was
          // never defined in the Tailwind theme and compiled to no CSS.
          <div className="absolute z-50 mt-1 w-full overflow-hidden rounded-card border border-line bg-surface shadow-[var(--shadow-lift)]">
            {showSearch && (
              <div className="flex items-center gap-2 border-b border-line px-3 py-2">
                <Search className="h-3.5 w-3.5 shrink-0 text-muted" aria-hidden="true" />
                <input
                  ref={searchRef}
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setHighlight(0);
                  }}
                  onKeyDown={handleKeyDown}
                  placeholder="Search..."
                  className="w-full text-body-m text-ink outline-none placeholder:text-muted"
                />
              </div>
            )}

            <div className="max-h-56 overflow-y-auto py-1" role="listbox">
              {filtered.length === 0 ? (
                <div className="px-3 py-4 text-center">
                  <p className="text-body-m text-muted">{query ? 'No matches found.' : emptyMessage}</p>
                  {!query && emptyAction && <div className="mt-2">{emptyAction}</div>}
                </div>
              ) : (
                filtered.map((option, index) => {
                  const isSelected = selected != null && getValue(selected) === getValue(option);
                  const description = getDescription(option);
                  return (
                    <button
                      key={getValue(option)}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onMouseEnter={() => setHighlight(index)}
                      onClick={() => commit(option)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left ${
                        index === highlight ? 'bg-surface-2' : ''
                      }`}
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-body-m text-ink">{getLabel(option)}</span>
                        {description && (
                          <span className="block truncate text-label-s text-muted">{description}</span>
                        )}
                      </span>
                      {isSelected && <Check className="h-4 w-4 shrink-0 text-brand-press" aria-hidden="true" />}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>

      {helperText && !error && <p className="mt-1 text-label-s text-muted">{helperText}</p>}
      {error && <p className="mt-1 text-label-s text-danger">{error}</p>}
    </div>
  );
}
