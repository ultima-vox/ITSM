import type { ReactNode } from 'react';
import { Input, Textarea, Select } from '@/components/ui';
import { useI18n } from '@/i18n';
import {
  FORM_FIELD_META,
  type FormDefinition,
  type FormField,
  type FormSection,
} from '@/api/metadata';

export type DynamicFormValues = Record<string, string>;

export interface DynamicFormProps {
  definition: FormDefinition;
  values: DynamicFormValues;
  onChange: (key: string, value: string) => void;
  /**
   * Called when a text/textarea field blurs (detail save-on-blur).
   * Selects still commit via onChange.
   */
  onCommit?: (key: string, value: string) => void;
  /** Field-level validation messages keyed by attributeKey */
  errors?: Record<string, string>;
  /** When true, all fields render read-only (e.g. resolved work item). */
  readOnly?: boolean;
  /** Optional per-field option label override (value → label). */
  optionLabels?: Record<string, Record<string, string>>;
  /** Optional per-field option list override. */
  optionLists?: Record<string, { value: string; label: string }[]>;
  className?: string;
  /** Layout density for grids. */
  layout?: 'create' | 'detail';
  /** Restrict which attribute keys to render (defaults to all in definition). */
  includeKeys?: string[];
  autoFocusFirst?: boolean;
}

function resolveLabel(
  labels: Record<string, string> | undefined,
  locale: string,
  fallback: string,
): string {
  if (!labels) return fallback;
  return labels[locale] ?? labels.en ?? labels.ru ?? Object.values(labels)[0] ?? fallback;
}

function isFieldVisible(field: FormField): boolean {
  // Light engine: only hide when CEL source is literally "false"
  if (field.visibleWhen?.source === 'false') return false;
  return true;
}

function isFieldReadOnly(field: FormField, forced?: boolean): boolean {
  if (forced) return true;
  if (field.readOnlyWhen?.source === 'true') return true;
  return false;
}

function SectionBlock({
  section,
  locale,
  children,
  layout,
}: {
  section: FormSection;
  locale: string;
  children: ReactNode;
  layout: 'create' | 'detail';
}) {
  const title = resolveLabel(section.labels, locale, section.key);
  return (
    <fieldset className={`dyn-form__section dyn-form__section--${layout}`}>
      <legend className="dyn-form__legend">{title}</legend>
      <div
        className={
          layout === 'detail'
            ? 'detail-fields detail-fields--enterprise dyn-form__fields'
            : 'dyn-form__fields dyn-form__fields--create'
        }
      >
        {children}
      </div>
    </fieldset>
  );
}

function FieldControl({
  field,
  value,
  onChange,
  onCommit,
  error,
  readOnly,
  locale,
  optionLabels,
  optionLists,
  autoFocus,
  t,
}: {
  field: FormField;
  value: string;
  onChange: (v: string) => void;
  onCommit?: (v: string) => void;
  error?: string;
  readOnly?: boolean;
  locale: string;
  optionLabels?: Record<string, string>;
  optionLists?: { value: string; label: string }[];
  autoFocus?: boolean;
  t: (key: string, vars?: Record<string, string | number>) => string;
}) {
  const meta = FORM_FIELD_META[field.attributeKey];
  const label = resolveLabel(
    meta?.labels,
    locale,
    field.attributeKey,
  );
  const ro = isFieldReadOnly(field, readOnly);
  const required = field.required && !ro;

  if (field.attributeKey === 'description' || meta?.type === 'RICH_TEXT') {
    return (
      <Textarea
        name={field.attributeKey}
        label={label}
        required={required}
        rows={4}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={(e) => onCommit?.(e.target.value)}
        error={error}
        disabled={ro}
        readOnly={ro}
        className={field.attributeKey === 'description' ? 'dyn-form__full' : undefined}
      />
    );
  }

  const isEnum =
    meta?.type === 'ENUM' ||
    meta?.type === 'SELECT' ||
    field.attributeKey === 'service' ||
    field.attributeKey === 'impact' ||
    field.attributeKey === 'urgency' ||
    field.attributeKey === 'priority';

  if (isEnum) {
    let options = optionLists;
    if (!options) {
      const values = meta?.enumValues ?? [];
      options = values.map((v) => ({
        value: v,
        label:
          optionLabels?.[v] ??
          (field.attributeKey === 'impact'
            ? t(`workItem.impact${v.charAt(0).toUpperCase()}${v.slice(1)}`)
            : field.attributeKey === 'urgency'
              ? t(`workItem.urgency${v.charAt(0).toUpperCase()}${v.slice(1)}`)
              : field.attributeKey === 'priority'
                ? t(`priority.${v}`)
                : field.attributeKey === 'service'
                  ? serviceLabel(v, t)
                  : v),
      }));
    }

    return (
      <Select
        name={field.attributeKey}
        label={label}
        required={required}
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          onCommit?.(e.target.value);
        }}
        error={error}
        disabled={ro}
        placeholder={
          field.attributeKey === 'service' ? t('create.selectService') : undefined
        }
        options={options}
      />
    );
  }

  return (
    <Input
      name={field.attributeKey}
      label={label}
      required={required}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={(e) => onCommit?.(e.target.value)}
      error={error}
      disabled={ro}
      readOnly={ro}
      autoFocus={autoFocus}
      className={field.attributeKey === 'title' ? 'dyn-form__full' : undefined}
    />
  );
}

function serviceLabel(
  value: string,
  t: (key: string) => string,
): string {
  if (value === 'workplace') return t('create.serviceWorkplace');
  if (value === 'access') return t('create.serviceAccess');
  if (value === 'apps') return t('create.serviceApps');
  // Free-text service from existing tickets
  return value;
}

/**
 * Light form engine: renders sections/fields from FormDefinition metadata.
 * Maps attribute keys onto plain string values (title, description, service, impact, urgency…).
 */
export function DynamicForm({
  definition,
  values,
  onChange,
  onCommit,
  errors = {},
  readOnly,
  optionLabels,
  optionLists,
  className = '',
  layout = 'create',
  includeKeys,
  autoFocusFirst,
}: DynamicFormProps) {
  const { locale, t } = useI18n();
  const allow = includeKeys ? new Set(includeKeys) : null;
  let focused = false;

  return (
    <div
      className={`dyn-form dyn-form--${layout} ${className}`.trim()}
      data-form-key={definition.key}
      data-form-version={definition.version}
    >
      {definition.sections.map((section) => {
        const fields = section.fields.filter((f) => {
          if (!isFieldVisible(f)) return false;
          if (allow && !allow.has(f.attributeKey)) return false;
          return true;
        });
        if (fields.length === 0) return null;
        return (
          <SectionBlock
            key={section.key}
            section={section}
            locale={locale}
            layout={layout}
          >
            {fields.map((field) => {
              const af = Boolean(autoFocusFirst && !focused && field.attributeKey === 'title');
              if (af) focused = true;
              return (
                <FieldControl
                  key={field.attributeKey}
                  field={field}
                  value={values[field.attributeKey] ?? ''}
                  onChange={(v) => onChange(field.attributeKey, v)}
                  onCommit={
                    onCommit
                      ? (v) => onCommit(field.attributeKey, v)
                      : undefined
                  }
                  error={errors[field.attributeKey]}
                  readOnly={readOnly}
                  locale={locale}
                  optionLabels={optionLabels?.[field.attributeKey]}
                  optionLists={optionLists?.[field.attributeKey]}
                  autoFocus={af}
                  t={t}
                />
              );
            })}
          </SectionBlock>
        );
      })}
    </div>
  );
}

/** Collect required attribute keys from a form definition. */
export function formRequiredKeys(definition: FormDefinition): string[] {
  return definition.sections.flatMap((s) =>
    s.fields.filter((f) => f.required && isFieldVisible(f)).map((f) => f.attributeKey),
  );
}
