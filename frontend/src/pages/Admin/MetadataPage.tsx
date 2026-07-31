import { useMemo, useState } from 'react';
import { Database, Layers } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { fetchObjectDefinitions, type ObjectDefinition } from '@/api';
import { Badge, EmptyState, ErrorState, SkeletonRows } from '@/components/ui';

function localizeLabel(
  labels: Record<string, string> | undefined,
  locale: string,
  fallback: string,
): string {
  if (!labels) return fallback;
  return labels[locale] ?? labels.en ?? labels.ru ?? fallback;
}

export function MetadataPage() {
  const t = useT();
  const { locale } = useI18n();
  const { data, loading, error, reload } = useAsync(() => fetchObjectDefinitions(), []);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const objects = data ?? [];
  const selected: ObjectDefinition | null = useMemo(() => {
    if (!objects.length) return null;
    const key = selectedKey ?? objects[0]?.key ?? null;
    return objects.find((o) => o.key === key) ?? objects[0] ?? null;
  }, [objects, selectedKey]);

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
          {loading && !data ? (
            <div className="metadata-objects__list">
              <SkeletonRows rows={3} />
            </div>
          ) : objects.length === 0 ? (
            <EmptyState title={t('metadata.emptyTitle')} description={t('metadata.emptyHint')} />
          ) : (
            <ul className="metadata-objects__list">
              {objects.map((obj) => {
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
                </div>
              </div>

              <div className="data-table-wrap">
                <table className="data-table">
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
                          <td>
                            {attr.searchable ? t('app.yes') : t('app.no')}
                          </td>
                          <td className="metadata-enums">
                            {attr.enumValues?.length
                              ? attr.enumValues.join(', ')
                              : '—'}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              {selected.relations.length > 0 && (
                <div className="metadata-relations">
                  <h3>{t('metadata.relations')}</h3>
                  <div className="data-table-wrap">
                    <table className="data-table">
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
            </>
          )}
        </div>
      </div>
    </section>
  );
}
