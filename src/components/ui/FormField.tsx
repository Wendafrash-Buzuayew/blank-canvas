import React, { useId } from 'react';

/**
 * DESIGN.md §6.5. Anatomy: label (always visible, never a placeholder),
 * control, optional hint, error. Renders its own <input>/<textarea>/<select>
 * via `as`, so every field in the product shares one focus/error treatment
 * instead of re-declaring it per form.
 */
export interface FormFieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
  as?: 'input' | 'textarea';
  rows?: number;
}

const CONTROL_CLASSES =
  'h-11 w-full rounded-control border bg-surface px-3 text-body-m text-ink placeholder:text-muted ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

export const FormField: React.FC<FormFieldProps> = ({
  label,
  hint,
  error,
  as = 'input',
  rows = 3,
  required,
  className = '',
  id,
  ...props
}) => {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const hintId = hint ? `${fieldId}-hint` : undefined;
  const errorId = error ? `${fieldId}-error` : undefined;

  const controlClassName = [
    CONTROL_CLASSES,
    error ? 'border-danger' : 'border-line',
    as === 'textarea' ? 'h-auto py-2.5 resize-y' : '',
    className,
  ].join(' ');

  return (
    <div className="w-full">
      <label htmlFor={fieldId} className="mb-1 block text-label-m text-ink">
        {label}
        {required && <span className="ml-0.5 text-danger">*</span>}
      </label>
      {as === 'textarea' ? (
        <textarea
          id={fieldId}
          rows={rows}
          required={required}
          aria-invalid={!!error}
          aria-describedby={errorId ?? hintId}
          className={controlClassName}
          {...(props as unknown as React.TextareaHTMLAttributes<HTMLTextAreaElement>)}
        />
      ) : (
        <input
          id={fieldId}
          required={required}
          aria-invalid={!!error}
          aria-describedby={errorId ?? hintId}
          className={controlClassName}
          {...props}
        />
      )}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-label-s text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-label-s text-danger">
          {error}
        </p>
      )}
    </div>
  );
};
