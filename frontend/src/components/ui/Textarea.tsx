import type { TextareaHTMLAttributes } from 'react';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export function Textarea({
  label,
  error,
  hint,
  id,
  className = '',
  ...rest
}: TextareaProps) {
  const inputId = id ?? rest.name;
  return (
    <label className={`field ${className}`.trim()} htmlFor={inputId}>
      {label && (
        <span className="field__label">
          {label}
          {rest.required && <span className="field__req" aria-hidden>*</span>}
        </span>
      )}
      <span
        className={`field__control field__control--textarea${error ? ' field__control--error' : ''}`}
      >
        <textarea id={inputId} {...rest} />
      </span>
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
      {!error && hint && <span className="field__hint">{hint}</span>}
    </label>
  );
}
