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
        <label className="block text-xs font-bold text-slate-700 mb-1">
          {label}
          {required && <span className="text-[#E60028] ml-0.5">*</span>}
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
          className={`w-full flex items-center justify-between gap-2 px-3 py-2 text-sm rounded-lg border text-left transition-colors ${
            error
              ? 'border-red-300 bg-red-50 text-red-700'
              : isDisabled
              ? 'border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed'
              : 'border-slate-300 bg-white text-slate-900 hover:border-slate-400 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]'
          }`}
        >
          <span className="truncate flex items-center gap-2">
            {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin shrink-0" />}
            {isLoading
              ? loadingMessage
              : error
              ? error
              : selected
              ? getLabel(selected)
              : <span className="text-slate-400">{placeholder}</span>}
          </span>
          <span className="flex items-center gap-1 shrink-0">
            {clearable && selected && !isDisabled && (
              <span
                role="button"
                tabIndex={-1}
                aria-label="Clear selection"
                onClick={(e) => {
                  e.stopPropagation();
                  commit(null);
                }}
                className="p-0.5 text-slate-400 hover:text-slate-700 rounded"
              >
                <X className="w-3.5 h-3.5" />
              </span>
            )}
            <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`} />
          </span>
        </button>

        {open && !isDisabled && (
          <div className="absolute z-50 mt-1 w-full bg-white border border-slate-200 rounded-xl shadow-lg overflow-hidden">
            {showSearch && (
              <div className="flex items-center gap-2 px-3 py-2 border-b border-slate-100">
                <Search className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <input
                  ref={searchRef}
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setHighlight(0);
                  }}
                  onKeyDown={handleKeyDown}
                  placeholder="Search..."
                  className="w-full text-sm outline-none placeholder:text-slate-400"
                />
              </div>
            )}

            <div className="max-h-56 overflow-y-auto py-1" role="listbox">
              {filtered.length === 0 ? (
                <div className="px-3 py-4 text-center">
                  <p className="text-xs text-slate-500">{query ? 'No matches found.' : emptyMessage}</p>
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
                      className={`w-full text-left px-3 py-2 flex items-center justify-between gap-2 ${
                        index === highlight ? 'bg-slate-50' : ''
                      }`}
                    >
                      <span className="min-w-0">
                        <span className="block text-sm text-slate-900 truncate">{getLabel(option)}</span>
                        {description && (
                          <span className="block text-[11px] text-slate-500 truncate">{description}</span>
                        )}
                      </span>
                      {isSelected && <Check className="w-4 h-4 text-[#E60028] shrink-0" />}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>

      {helperText && !error && <p className="text-[11px] text-slate-500 mt-1">{helperText}</p>}
      {error && <p className="text-[11px] text-red-600 mt-1">{error}</p>}
    </div>
  );
}
