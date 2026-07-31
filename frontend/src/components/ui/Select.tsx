import type { SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { value: string; label: string; disabled?: boolean }[];
  placeholder?: string;
}

export function Select({
  label,
  error,
  options,
  placeholder,
  id,
  className = '',
  ...rest
}: SelectProps) {
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
        <select id={inputId} {...rest}>
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((o) => (
            <option key={o.value} value={o.value} disabled={o.disabled}>
              {o.label}
            </option>
          ))}
        </select>
      </span>
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
