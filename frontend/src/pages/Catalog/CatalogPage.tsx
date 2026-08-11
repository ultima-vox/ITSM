import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  Clock3,
  KeyRound,
  Laptop,
  MonitorCog,
  Search,
  Shield,
  Sparkles,
  X,
  CheckCircle2,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useShell } from '@/hooks/useShell';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import {
  createWorkItem,
  fetchCatalogCategories,
  fetchCatalogServices,
  fetchFormDefinition,
  submitCatalogRequest,
  isMockMode,
  type FormDefinition,
} from '@/api';
import { DynamicForm, formRequiredKeys } from '@/components/form/DynamicForm';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import type {
  CatalogService,
  ImpactLevel,
  UrgencyLevel,
  WorkItem,
} from '@/types';

const iconMap = {
  key: KeyRound,
  laptop: Laptop,
  monitor: MonitorCog,
  shield: Shield,
  cloud: MonitorCog,
  server: MonitorCog,
} as const;

/** Catalog requests: impact is optional; service prefilled as free text. */
function catalogFormDefinition(base: FormDefinition | null): FormDefinition | null {
  if (!base) return null;
  return {
    ...base,
    sections: base.sections.map((section) => ({
      ...section,
      fields: section.fields.map((field) =>
        field.attributeKey === 'impact'
          ? { ...field, required: false }
          : field.attributeKey === 'service'
            ? { ...field, required: false }
            : field,
      ),
    })),
  };
}

export function CatalogPage() {
  const t = useT();
  const { openCommand } = useShell();
  const { success } = useToast();
  const [query, setQuery] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [drawerService, setDrawerService] = useState<CatalogService | null>(null);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const categories = useAsync(() => fetchCatalogCategories(), []);
  const services = useAsync(() => fetchCatalogServices(), []);

  const allServices = services.data ?? [];
  const cats = categories.data ?? [];
  const loadError = categories.error || services.error;
  const reloadAll = () => {
    categories.reload();
    services.reload();
  };

  const filteredServices = useMemo(() => {
    return allServices.filter((s) => {
      if (categoryId && s.categoryId !== categoryId) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        t(s.titleKey).toLowerCase().includes(q) ||
        t(s.descriptionKey).toLowerCase().includes(q)
      );
    });
  }, [allServices, categoryId, query, t]);

  const popular = useMemo(
    () => filteredServices.filter((s) => s.popular),
    [filteredServices],
  );

  const filteredCategories = useMemo(() => {
    if (!query.trim() && !categoryId) return cats;
    if (categoryId) return cats.filter((c) => c.id === categoryId);
    const q = query.toLowerCase();
    return cats.filter((c) => {
      const title = t(c.titleKey).toLowerCase();
      const desc = t(c.descriptionKey).toLowerCase();
      return title.includes(q) || desc.includes(q);
    });
  }, [cats, query, categoryId, t]);

  const smartSuggestions = useMemo(() => {
    const byId = (id: string) => allServices.find((s) => s.id === id);
    return [
      byId('svc-vpn') ?? allServices[0],
      byId('svc-access') ?? allServices[1],
      byId('svc-hardware') ?? allServices[2],
    ].filter(Boolean) as CatalogService[];
  }, [allServices]);

  const hasActiveFilters = Boolean(query.trim() || categoryId);
  const clearFilters = () => {
    setQuery('');
    setCategoryId(null);
  };

  if (loadError && !categories.loading && !services.loading && !categories.data && !services.data) {
    return (
      <section className="page page--catalog">
        <div className="catalog-hero">
          <div className="catalog-kicker">{t('catalog.kicker')}</div>
          <h1>{t('catalog.title')}</h1>
        </div>
        <ErrorState onRetry={reloadAll} />
      </section>
    );
  }

  return (
    <section className="page page--catalog">
      <div className="catalog-hero">
        <div className="catalog-kicker">{t('catalog.kicker')}</div>
        <h1>{t('catalog.title')}</h1>
        <p>{t('catalog.subtitle')}</p>
        <label className="catalog-search">
          <Search size={20} aria-hidden />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('catalog.searchPlaceholder')}
            aria-label={t('catalog.searchPlaceholder')}
          />
          {query && (
            <button
              type="button"
              className="icon-btn"
              aria-label={t('app.clearSearch')}
              onClick={() => setQuery('')}
            >
              <X size={16} />
            </button>
          )}
        </label>
      </div>

      <div className="filter-chips" role="toolbar" aria-label={t('app.filters')}>
        <button
          type="button"
          className={`chip chip--toggle${categoryId === null ? ' is-on' : ''}`}
          onClick={() => setCategoryId(null)}
        >
          {t('app.all')}
        </button>
        {cats.map((c) => (
          <button
            key={c.id}
            type="button"
            className={`chip chip--toggle${categoryId === c.id ? ' is-on' : ''}`}
            onClick={() => setCategoryId(categoryId === c.id ? null : c.id)}
          >
            {t(c.titleKey)}
            <b>{c.count}</b>
          </button>
        ))}
      </div>

      <div className="catalog-layout">
        <section>
          <div className="section-head">
            <div>
              <h2>{t('catalog.popular')}</h2>
              <p>{t('catalog.popularHint')}</p>
            </div>
            <span className="muted">
              {t('app.showingOf', {
                shown: popular.length || filteredServices.length,
                total: allServices.length,
              })}
            </span>
          </div>

          {services.loading ? (
            <div className="service-grid">
              {Array.from({ length: 3 }).map((_, i) => (
                <div className="service-card service-card--skeleton" key={i}>
                  <Skeleton width={34} height={34} radius={9} />
                  <Skeleton width="70%" height={12} />
                  <Skeleton width="90%" height={10} />
                  <Skeleton width="50%" height={10} />
                </div>
              ))}
            </div>
          ) : filteredServices.length === 0 ? (
            <div className="catalog-empty-results">
              <EmptyState
                title={
                  hasActiveFilters
                    ? t('catalog.emptyFilteredTitle')
                    : t('catalog.emptyTitle')
                }
                description={
                  hasActiveFilters
                    ? t('catalog.emptyFilteredHint')
                    : t('catalog.emptyHint')
                }
                actionLabel={
                  hasActiveFilters ? t('catalog.clearFilters') : t('app.clearSearch')
                }
                onAction={clearFilters}
              />
              {hasActiveFilters && allServices.length > 0 && (
                <p className="catalog-empty-results__hint">
                  {t('catalog.emptyTryBrowse', { n: allServices.length })}
                </p>
              )}
            </div>
          ) : (
            <div className="service-grid">
              {(popular.length > 0 ? popular : filteredServices).map((svc) => {
                const Icon = iconMap[svc.icon] ?? KeyRound;
                return (
                  <button
                    key={svc.id}
                    type="button"
                    className="service-card"
                    onClick={() => setDrawerService(svc)}
                  >
                    <span>
                      <Icon size={17} />
                    </span>
                    <b>{t(svc.titleKey)}</b>
                    <p>{t(svc.descriptionKey)}</p>
                    <small>
                      <Clock3 size={12} aria-hidden />
                      {t(svc.metaKey)}
                    </small>
                    <ArrowRight className="service-arrow" size={17} aria-hidden />
                  </button>
                );
              })}
            </div>
          )}

          <div className="section-head section-head--spaced">
            <div>
              <h2>{t('catalog.categoriesHeading')}</h2>
            </div>
          </div>

          {categories.loading ? (
            <div className="category-grid">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} height={72} radius={10} />
              ))}
            </div>
          ) : filteredCategories.length === 0 ? (
            <div className="catalog-empty-results">
              <EmptyState
                title={t('catalog.emptyCategoriesTitle')}
                description={t('catalog.emptyCategoriesHint')}
                actionLabel={t('catalog.clearFilters')}
                onAction={clearFilters}
              />
            </div>
          ) : (
            <div className="category-grid">
              {filteredCategories.map((cat) => {
                const Icon = iconMap[cat.icon] ?? KeyRound;
                return (
                  <button
                    type="button"
                    className={`category${categoryId === cat.id ? ' is-active' : ''}`}
                    key={cat.id}
                    onClick={() =>
                      setCategoryId(categoryId === cat.id ? null : cat.id)
                    }
                  >
                    <span className={`category-icon category-icon--${cat.tone}`}>
                      <Icon size={17} />
                    </span>
                    <span>
                      <b>{t(cat.titleKey)}</b>
                      <small>{t(cat.descriptionKey)}</small>
                      <em>{t('catalog.servicesCount', { n: cat.count })}</em>
                    </span>
                    <ArrowRight size={16} aria-hidden />
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <aside className="catalog-aside">
          <div className="catalog-help">
            <span>
              <Sparkles size={19} />
            </span>
            <h3>{t('catalog.helpTitle')}</h3>
            <p>{t('catalog.helpText')}</p>
            <button type="button" onClick={() => setAssistantOpen(true)}>
              {t('catalog.askAssistant')} <ArrowRight size={15} />
            </button>
          </div>
          <div className="catalog-knowledge">
            <BookOpen size={19} />
            <div>
              <h3>{t('catalog.knowledgeTitle')}</h3>
              <Link to="/knowledge">{t('knowledge.articles.vpn.title')}</Link>
              <Link to="/knowledge">{t('knowledge.articles.data.title')}</Link>
            </div>
          </div>
        </aside>
      </div>

      {drawerService && (
        <ServiceDrawer
          service={drawerService}
          onClose={() => setDrawerService(null)}
          onCreated={(item) => {
            setDrawerService(null);
            success(t('catalog.requestCreated', { n: item.number }), {
              label: t('catalog.openWorkItem', { n: item.number }),
              href: `/work-items/${item.id}`,
            });
          }}
        />
      )}

      {assistantOpen && (
        <AssistantPanel
          suggestions={smartSuggestions}
          onClose={() => setAssistantOpen(false)}
          onPickService={(svc) => {
            setAssistantOpen(false);
            setDrawerService(svc);
          }}
          onOpenCommand={() => {
            setAssistantOpen(false);
            openCommand();
          }}
        />
      )}
    </section>
  );
}

function ServiceDrawer({
  service,
  onClose,
  onCreated,
}: {
  service: CatalogService;
  onClose: () => void;
  onCreated: (item: WorkItem) => void;
}) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref, true);
  const Icon = iconMap[service.icon] ?? KeyRound;
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [baseForm, setBaseForm] = useState<FormDefinition | null>(null);
  const [values, setValues] = useState<Record<string, string>>({
    title: t(service.titleKey),
    description: '',
    service: t(service.titleKey),
    impact: '',
    urgency: service.id === 'svc-incident' ? 'high' : 'medium',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const formDef = useMemo(() => catalogFormDefinition(baseForm), [baseForm]);

  useEffect(() => {
    let cancelled = false;
    void fetchFormDefinition('work-item').then((def) => {
      if (!cancelled) setBaseForm(def);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setValues({
      title: t(service.titleKey),
      description: '',
      service: t(service.titleKey),
      impact: '',
      urgency: service.id === 'svc-incident' ? 'high' : 'medium',
    });
    setErrors({});
    setDone(false);
  }, [service, t]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  const setField = (key: string, value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((e) => {
        const next = { ...e };
        delete next[key];
        return next;
      });
    }
  };

  const validate = () => {
    const next: Record<string, string> = {};
    const required = formDef
      ? formRequiredKeys(formDef).filter((k) => k !== 'service' && k !== 'impact')
      : ['title', 'description'];
    // Always require title + description for catalog; impact optional
    for (const key of required) {
      if (!(values[key] ?? '').trim()) {
        if (key === 'title') next.title = t('create.validationTitle');
        else if (key === 'description') next.description = t('create.validationDetails');
        else if (key === 'urgency') next.urgency = t('app.required');
        else next[key] = t('app.required');
      }
    }
    if (!(values.title ?? '').trim()) next.title = t('create.validationTitle');
    if (!(values.description ?? '').trim()) {
      next.description = t('create.validationDetails');
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleRequest = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const kind = service.id === 'svc-incident' ? 'incident' : 'request';
      const impactRaw = (values.impact ?? '').trim();
      const formPayload = {
        title: values.title.trim(),
        description: values.description.trim(),
        service: (values.service || t(service.titleKey)).trim(),
        urgency: values.urgency,
        impact: impactRaw || undefined,
      };

      // Live backend: prefer catalog request endpoint for UUID catalog items
      if (!isMockMode() && /^[0-9a-f-]{36}$/i.test(service.id)) {
        const submitted = await submitCatalogRequest(service.id, { formPayload });
        const item: WorkItem = {
          id: submitted.workItemId ?? submitted.id,
          number: submitted.number ?? submitted.id.slice(0, 8).toUpperCase(),
          title: values.title.trim(),
          description: values.description.trim(),
          type: kind,
          priority: kind === 'incident' ? 'high' : 'medium',
          status: 'new',
          assignee: null,
          requester: {
            id: 'me',
            name: 'You',
            initials: 'YO',
          },
          service: formPayload.service,
          slaTarget: '—',
          slaState: 'on_track',
          updatedAt: new Date().toISOString(),
          createdAt: new Date().toISOString(),
          queue: 'Service Desk L1',
        };
        setDone(true);
        onCreated(item);
        return;
      }

      const item = await createWorkItem({
        kind,
        title: values.title.trim(),
        description: values.description.trim(),
        service: formPayload.service,
        priority: kind === 'incident' ? 'high' : 'medium',
        queue: 'Service Desk L1',
        impact: impactRaw
          ? (impactRaw as ImpactLevel)
          : kind === 'incident'
            ? 'high'
            : 'medium',
        urgency: (values.urgency as UrgencyLevel) || (kind === 'incident' ? 'high' : 'medium'),
      });
      setDone(true);
      onCreated(item);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="drawer-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside
        ref={ref}
        className="service-drawer service-drawer--form"
        role="dialog"
        aria-modal="true"
        aria-labelledby="svc-drawer-title"
      >
        <div className="service-drawer__head">
          <span className="service-drawer__icon">
            <Icon size={20} />
          </span>
          <button
            type="button"
            className="icon-btn"
            aria-label={t('app.close')}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        {done ? (
          <div className="service-drawer__success">
            <CheckCircle2 size={28} aria-hidden />
            <h2>{t('catalog.requestSuccessTitle')}</h2>
            <p>{t('catalog.requestSuccessHint')}</p>
          </div>
        ) : (
          <>
            <p className="eyebrow">{t('catalog.kicker')}</p>
            <h2 id="svc-drawer-title">{t(service.titleKey)}</h2>
            <p className="service-drawer__desc">{t(service.descriptionKey)}</p>
            <dl className="service-drawer__meta">
              <div>
                <dt>{t('workItem.sla')}</dt>
                <dd>{t(service.metaKey)}</dd>
              </div>
              <div>
                <dt>{t('catalog.approval')}</dt>
                <dd>
                  {service.approvalRequired
                    ? t('catalog.approvalRequired')
                    : t('catalog.approvalNone')}
                </dd>
              </div>
            </dl>

            <div className="service-drawer__form">
              <p className="service-drawer__form-label">{t('catalog.requestDetails')}</p>
              {formDef ? (
                <DynamicForm
                  definition={formDef}
                  values={values}
                  onChange={setField}
                  errors={errors}
                  layout="create"
                  autoFocusFirst
                  optionLists={{
                    service: [
                      {
                        value: t(service.titleKey),
                        label: t(service.titleKey),
                      },
                    ],
                    impact: [
                      { value: '', label: t('catalog.impactOptional') },
                      { value: 'high', label: t('workItem.impactHigh') },
                      { value: 'medium', label: t('workItem.impactMedium') },
                      { value: 'low', label: t('workItem.impactLow') },
                    ],
                  }}
                />
              ) : (
                <div className="service-drawer__form-skeleton" aria-busy="true">
                  <Skeleton height={40} radius={8} />
                  <Skeleton height={80} radius={8} />
                  <Skeleton height={40} radius={8} />
                </div>
              )}
            </div>

            <div className="service-drawer__actions">
              <Button
                variant="primary"
                fullWidth
                disabled={submitting || !formDef}
                onClick={() => void handleRequest()}
              >
                {submitting ? t('app.loading') : t('catalog.requestService')}
              </Button>
              <Button variant="secondary" fullWidth onClick={onClose}>
                {t('app.cancel')}
              </Button>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}

function AssistantPanel({
  suggestions,
  onClose,
  onPickService,
  onOpenCommand,
}: {
  suggestions: CatalogService[];
  onClose: () => void;
  onPickService: (svc: CatalogService) => void;
  onOpenCommand: () => void;
}) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref, true);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  return (
    <div
      className="drawer-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside
        ref={ref}
        className="assistant-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="catalog-assistant-title"
      >
        <div className="assistant-panel__head">
          <span className="assistant-panel__icon">
            <Sparkles size={18} />
          </span>
          <div>
            <h2 id="catalog-assistant-title">{t('catalog.assistantTitle')}</h2>
            <p>{t('catalog.assistantHint')}</p>
          </div>
          <button
            type="button"
            className="icon-btn"
            aria-label={t('app.close')}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        <ul className="assistant-panel__suggestions">
          {suggestions.map((svc, i) => {
            const Icon = iconMap[svc.icon] ?? KeyRound;
            return (
              <li key={svc.id}>
                <button type="button" onClick={() => onPickService(svc)}>
                  <span className="assistant-panel__rank">{i + 1}</span>
                  <span className="assistant-panel__svc-icon">
                    <Icon size={16} />
                  </span>
                  <span>
                    <b>{t(svc.titleKey)}</b>
                    <small>{t(svc.descriptionKey)}</small>
                  </span>
                  <ArrowRight size={15} aria-hidden />
                </button>
              </li>
            );
          })}
        </ul>
        <button
          type="button"
          className="assistant-panel__cmd"
          onClick={onOpenCommand}
        >
          {t('catalog.openCommandPalette')}
        </button>
      </aside>
    </div>
  );
}
