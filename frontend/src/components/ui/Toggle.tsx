interface ToggleProps {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  description?: string;
  id?: string;
}

export function Toggle({ checked, onChange, label, description, id }: ToggleProps) {
  const toggleId = id ?? label.replace(/\s+/g, '-').toLowerCase();
  return (
    <label className="toggle" htmlFor={toggleId}>
      <span className="toggle__text">
        <span className="toggle__label">{label}</span>
        {description && <span className="toggle__desc">{description}</span>}
      </span>
      <input
        id={toggleId}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="toggle__track" aria-hidden>
        <span className="toggle__thumb" />
      </span>
    </label>
  );
}
