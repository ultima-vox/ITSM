import type { InputHTMLAttributes, ReactNode } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
}

export function Input({
  label,
  hint,
  error,
  leading,
  trailing,
  id,
  className = '',
  ...rest
}: InputProps) {
  const inputId = id ?? rest.name;
  return (
    <label className={`field ${className}`.trim()} htmlFor={inputId}>
      {label && (
        <span className="field__label">
          {label}
          {rest.required && <span className="field__req" aria-hidden>*</span>}
        </span>
      )}
      <span className={`field__control${error ? ' field__control--error' : ''}`}>
        {leading}
        <input id={inputId} {...rest} />
        {trailing}
      </span>
      {error ? (
        <span className="field__error" role="alert">
          {error}
        </span>
      ) : hint ? (
        <span className="field__hint">{hint}</span>
      ) : null}
    </label>
  );
}
