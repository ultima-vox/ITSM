import { useEffect, useMemo, useState } from 'react';
import { Database, FileText, Layers, Search, Workflow, X } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import {
  fetchObjectDefinitions,
  fetchFormDefinition,
  type ObjectDefinition,
  type FormDefinition,
  type MetadataAttribute,
} from '@/api';
import { DynamicForm } from '@/components/form/DynamicForm';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  SkeletonRows,
} from '@/components/ui';

function localizeLabel(
  labels: Record<string, string> | undefined,
  locale: string,
  fallback: string,
): string {
  if (!labels) return fallback;
  return labels[locale] ?? labels.en ?? labels.ru ?? fallback;
}

/** Prefer state/status ENUM attributes as mock workflow definition. */
function workflowStatesFromObject(obj: ObjectDefinition): MetadataAttribute | null {
  const preferred = obj.attributes.find(
    (a) =>
      (a.key === 'state' || a.key === 'status') &&
      a.enumValues &&
      a.enumValues.length > 0,
  );
  if (preferred) return preferred;
  return (
    obj.attributes.find((a) => a.type === 'ENUM' && a.enumValues && a.enumValues.length > 1) ??
    null
  );
}

export function MetadataPage() {
  const t = useT();
  const { locale } = useI18n();
  const { data, loading, error, reload } = useAsync(() => fetchObjectDefinitions(), []);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [objectQuery, setObjectQuery] = useState('');
  const [formPreviewOpen, setFormPreviewOpen] = useState(false);
  const [formDef, setFormDef] = useState<FormDefinition | null>(null);
  const [formLoading, setFormLoading] = useState(false);

  const objects = data ?? [];

  const filteredObjects = useMemo(() => {
    const q = objectQuery.trim().toLowerCase();
    if (!q) return objects;
    return objects.filter((obj) => {
      const label = localizeLabel(obj.labels, locale, obj.key).toLowerCase();
      return (
        obj.key.toLowerCase().includes(q) ||
        label.includes(q) ||
        obj.attributes.some(
          (a) =>
            a.key.toLowerCase().includes(q) ||
            localizeLabel(a.labels, locale, a.key).toLowerCase().includes(q),
        )
      );
    });
  }, [objects, objectQuery, locale]);

  const selected: ObjectDefinition | null = useMemo(() => {
    if (!filteredObjects.length) {
      // Keep selection if it still exists in full list but filter hid it
      if (selectedKey) {
        return objects.find((o) => o.key === selectedKey) ?? null;
      }
      return null;
    }
    const key = selectedKey ?? filteredObjects[0]?.key ?? null;
    const fromFiltered = filteredObjects.find((o) => o.key === key);
    if (fromFiltered) return fromFiltered;
    return filteredObjects[0] ?? null;
  }, [filteredObjects, objects, selectedKey]);

  const workflowAttr = selected ? workflowStatesFromObject(selected) : null;

  const selectedObjectKey = selected?.key ?? null;

  useEffect(() => {
    if (!selectedObjectKey) {
      setFormDef(null);
      setFormLoading(false);
      return;
    }
    let cancelled = false;
    setFormLoading(true);
    void fetchFormDefinition(selectedObjectKey).then((def) => {
      if (!cancelled) {
        setFormDef(def);
        setFormLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [selectedObjectKey]);

  if (error && !loading && !data) {
    return (
      <section className="page page--metadata">
        <div className="page-head">
          <div>
            <h1>{t('metadata.title')}</h1>
            <p className="page-subtitle">{t('metadata.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  return (
    <section className="page page--metadata">
      <div className="page-head">
        <div>
          <h1>{t('metadata.title')}</h1>
          <p className="page-subtitle">{t('metadata.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Layers size={14} aria-hidden />
            {t('metadata.objectCount', { n: objects.length })}
          </span>
          <span className="chip chip--muted">{t('metadata.readOnly')}</span>
        </div>
      </div>

      <div className="metadata-layout">
        <aside className="panel metadata-objects" aria-label={t('metadata.objects')}>
          <div className="metadata-objects__head">
            <Database size={16} aria-hidden />
            <h2>{t('metadata.objects')}</h2>
          </div>
          <div className="metadata-objects__search">
            <Input
              name="metadata-object-search"
              leading={<Search size={14} aria-hidden />}
              trailing={
                objectQuery ? (
                  <button
                    type="button"
                    className="icon-btn icon-btn--sm"
                    aria-label={t('app.clearSearch')}
                    onClick={() => setObjectQuery('')}
                  >
                    <X size={14} />
                  </button>
                ) : undefined
              }
              value={objectQuery}
              onChange={(e) => setObjectQuery(e.target.value)}
              placeholder={t('metadata.searchPlaceholder')}
              aria-label={t('metadata.searchPlaceholder')}
            />
          </div>
          {loading && !data ? (
            <div className="metadata-objects__list">
              <SkeletonRows rows={3} />
            </div>
          ) : objects.length === 0 ? (
            <EmptyState title={t('metadata.emptyTitle')} description={t('metadata.emptyHint')} />
          ) : filteredObjects.length === 0 ? (
            <EmptyState
              title={t('metadata.filterEmptyTitle')}
              description={t('metadata.filterEmptyHint')}
              actionLabel={t('app.clearSearch')}
              onAction={() => setObjectQuery('')}
            />
          ) : (
            <ul className="metadata-objects__list">
              {filteredObjects.map((obj) => {
                const active = selected?.key === obj.key;
                return (
                  <li key={obj.key}>
                    <button
                      type="button"
                      className={`metadata-object-btn${active ? ' is-active' : ''}`}
                      onClick={() => setSelectedKey(obj.key)}
                      aria-current={active ? 'true' : undefined}
                    >
                      <span className="metadata-object-btn__key mono">{obj.key}</span>
                      <span className="metadata-object-btn__label">
                        {localizeLabel(obj.labels, locale, obj.key)}
                      </span>
                      <span className="metadata-object-btn__meta">
                        v{obj.version} · {obj.attributes.length} {t('metadata.attrsShort')}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        <div className="panel panel--flush metadata-detail">
          {!selected ? (
            <div className="metadata-detail__empty">
              <EmptyState
                title={t('metadata.selectTitle')}
                description={t('metadata.selectHint')}
              />
            </div>
          ) : (
            <>
              <div className="metadata-detail__head">
                <div>
                  <p className="metadata-detail__kicker">{t('metadata.objectDefinition')}</p>
                  <h2>
                    <code className="mono">{selected.key}</code>
                    <span className="metadata-detail__label">
                      {localizeLabel(selected.labels, locale, selected.key)}
                    </span>
                  </h2>
                </div>
                <div className="metadata-detail__badges">
                  <Badge>{t('metadata.version', { n: selected.version })}</Badge>
                  <Badge tone="neutral">
                    {t('metadata.attrCount', { n: selected.attributes.length })}
                  </Badge>
                  {selected.relations.length > 0 && (
                    <Badge tone="neutral">
                      {t('metadata.relCount', { n: selected.relations.length })}
                    </Badge>
                  )}
                  {formDef && (
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={<FileText size={14} />}
                      onClick={() => setFormPreviewOpen(true)}
                    >
                      {t('metadata.formPreview')}
                    </Button>
                  )}
                  {!formDef && !formLoading && (
                    <span className="chip chip--muted">{t('metadata.noForm')}</span>
                  )}
                </div>
              </div>

              {workflowAttr?.enumValues && workflowAttr.enumValues.length > 0 && (
                <div className="metadata-workflow">
                  <div className="metadata-workflow__head">
                    <Workflow size={15} aria-hidden />
                    <h3>
                      {t('metadata.workflowStates')}
                      <small className="mono">
                        {workflowAttr.key}
                      </small>
                    </h3>
                  </div>
                  <ol className="metadata-workflow__track" aria-label={t('metadata.workflowStates')}>
                    {workflowAttr.enumValues.map((state, i) => (
                      <li key={state} className="metadata-workflow__step">
                        {i > 0 && <span className="metadata-workflow__connector" aria-hidden />}
                        <span className="metadata-workflow__pill">
                          <code>{state}</code>
                        </span>
                      </li>
                    ))}
                  </ol>
                  <p className="metadata-workflow__note">{t('metadata.workflowMockNote')}</p>
                </div>
              )}

              <div className="metadata-section-label">
                <h3>{t('metadata.attributes')}</h3>
              </div>

              {selected.attributes.length === 0 ? (
                <div className="metadata-detail__empty metadata-detail__empty--inline">
                  <EmptyState
                    title={t('metadata.attrsEmptyTitle')}
                    description={t('metadata.attrsEmptyHint')}
                  />
                </div>
              ) : (
                <div className="data-table-wrap data-table-wrap--dense">
                  <table className="data-table data-table--dense">
                    <thead>
                      <tr>
                        <th scope="col">{t('metadata.colAttribute')}</th>
                        <th scope="col">{t('metadata.colLabel')}</th>
                        <th scope="col">{t('metadata.colType')}</th>
                        <th scope="col">{t('metadata.colRequired')}</th>
                        <th scope="col">{t('metadata.colSearchable')}</th>
                        <th scope="col">{t('metadata.colEnums')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {loading && !data ? (
                        <tr>
                          <td colSpan={6}>
                            <SkeletonRows rows={5} />
                          </td>
                        </tr>
                      ) : (
                        selected.attributes.map((attr) => (
                          <tr key={attr.key}>
                            <td>
                              <b className="mono">{attr.key}</b>
                            </td>
                            <td>{localizeLabel(attr.labels, locale, attr.key)}</td>
                            <td>
                              <code className="meta-type-pill">{attr.type}</code>
                            </td>
                            <td>
                              {attr.required ? (
                                <span className="api-mode-pill api-mode-pill--live">
                                  {t('app.yes')}
                                </span>
                              ) : (
                                <span className="api-mode-pill api-mode-pill--mock">
                                  {t('app.no')}
                                </span>
                              )}
                            </td>
                            <td>{attr.searchable ? t('app.yes') : t('app.no')}</td>
                            <td className="metadata-enums">
                              {attr.enumValues?.length ? attr.enumValues.join(', ') : '—'}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              )}

              {selected.relations.length > 0 && (
                <div className="metadata-relations">
                  <h3>{t('metadata.relations')}</h3>
                  <div className="data-table-wrap data-table-wrap--dense">
                    <table className="data-table data-table--dense">
                      <thead>
                        <tr>
                          <th scope="col">{t('metadata.colRelation')}</th>
                          <th scope="col">{t('metadata.colTarget')}</th>
                          <th scope="col">{t('metadata.colCardinality')}</th>
                          <th scope="col">{t('metadata.colRequired')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selected.relations.map((rel) => (
                          <tr key={rel.key}>
                            <td>
                              <b className="mono">{rel.key}</b>
                              <small className="cell-sub">
                                {localizeLabel(rel.labels, locale, rel.key)}
                              </small>
                            </td>
                            <td>
                              <code className="mono">{rel.targetObjectKey}</code>
                            </td>
                            <td>
                              <code className="meta-type-pill">{rel.cardinality}</code>
                            </td>
                            <td>{rel.required ? t('app.yes') : t('app.no')}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {selected.relations.length === 0 && (
                <div className="metadata-relations metadata-relations--empty">
                  <h3>{t('metadata.relations')}</h3>
                  <p className="metadata-empty-inline">{t('metadata.relsEmptyHint')}</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <Modal
        open={formPreviewOpen && Boolean(formDef)}
        onClose={() => setFormPreviewOpen(false)}
        size="md"
        labelledBy="metadata-form-preview-title"
        className="metadata-form-preview-modal"
      >
        {formDef && (
          <>
            <div className="dialog-head">
              <div>
                <p className="eyebrow">{t('metadata.formPreviewKicker')}</p>
                <h2 id="metadata-form-preview-title">
                  <code className="mono">{formDef.key}</code>
                  <span className="metadata-detail__label">
                    {t('metadata.formPreviewTitle', {
                      object: selected
                        ? localizeLabel(selected.labels, locale, selected.key)
                        : formDef.objectKey,
                    })}
                  </span>
                </h2>
              </div>
              <button
                type="button"
                className="icon-btn"
                aria-label={t('app.close')}
                onClick={() => setFormPreviewOpen(false)}
              >
                <X size={18} />
              </button>
            </div>
            <p className="panel-hint">{t('metadata.formPreviewHint')}</p>
            <div className="metadata-form-preview">
              <DynamicForm
                definition={formDef}
                values={{
                  title: '',
                  description: '',
                  service: '',
                  impact: 'medium',
                  urgency: 'medium',
                }}
                onChange={() => undefined}
                readOnly
                layout="detail"
              />
            </div>
            <div className="dialog-actions">
              <Button variant="secondary" onClick={() => setFormPreviewOpen(false)}>
                {t('app.close')}
              </Button>
            </div>
          </>
        )}
      </Modal>
    </section>
  );
}
