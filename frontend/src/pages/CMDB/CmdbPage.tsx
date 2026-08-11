import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from 'react';
import {
  ArrowRight,
  Boxes,
  CheckCircle2,
  Cloud,
  Download,
  Network,
  Pencil,
  Plus,
  Search,
  Server,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import {
  createCiRelation,
  createConfigurationItem,
  deleteCiRelation,
  fetchCiRelations,
  fetchConfigurationItems,
  subscribeConfigurationItems,
  updateCiRelation,
  updateConfigurationItem,
} from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Select,
  Skeleton,
} from '@/components/ui';
import { StatusChip } from '@/components/data-display';
import type {
  CiRelation,
  CiRelationType,
  CiStatus,
  ConfigurationItem,
  ImpactLevel,
} from '@/types';

/** Full relation type set matching mock graph vocabulary */
const EDITABLE_REL_TYPES: CiRelationType[] = [
  'depends_on',
  'hosted_on',
  'runs_on',
  'connects_to',
  'uses',
];

function displayRelType(type: CiRelationType): CiRelationType {
  return type === 'hosts' ? 'hosted_on' : type;
}

function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

function downloadCiListCsv(
  items: ConfigurationItem[],
  kindLabel: (key: string) => string,
  statusLabel: (s: CiStatus) => string,
  filename: string,
) {
  const headers = [
    'id',
    'name',
    'kind',
    'status',
    'owner',
    'environment',
    'criticality',
    'icon',
  ];
  const rows = items.map((c) =>
    [
      c.id,
      c.name,
      kindLabel(c.kindKey),
      statusLabel(c.status),
      c.owner,
      c.environment ?? '',
      c.criticality ?? '',
      c.icon,
    ]
      .map((v) => csvEscape(String(v)))
      .join(','),
  );
  const blob = new Blob([[headers.join(','), ...rows].join('\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

const icons = {
  server: Server,
  cloud: Cloud,
  network: Network,
  database: Boxes,
  app: Boxes,
} as const;

/** Seed SVG layout for the interactive dependency graph (viewBox 0 0 400 280) */
const SEED_GRAPH_LAYOUT: Record<string, { x: number; y: number }> = {
  'ci-portal': { x: 200, y: 42 },
  'ci-prod-api': { x: 90, y: 130 },
  'ci-pg-cluster': { x: 310, y: 130 },
  'ci-vpn-gw': { x: 90, y: 230 },
  'ci-net-ams01': { x: 310, y: 230 },
};

/** Free slots used when new CIs are added (orphan nodes, dim style) */
const ORPHAN_SLOTS: { x: number; y: number }[] = [
  { x: 200, y: 180 },
  { x: 50, y: 50 },
  { x: 350, y: 50 },
  { x: 50, y: 180 },
  { x: 350, y: 180 },
  { x: 200, y: 260 },
  { x: 150, y: 90 },
  { x: 250, y: 90 },
];

function buildGraphLayout(
  items: ConfigurationItem[],
): Record<string, { x: number; y: number; orphan?: boolean }> {
  const layout: Record<string, { x: number; y: number; orphan?: boolean }> = {};
  for (const [id, pos] of Object.entries(SEED_GRAPH_LAYOUT)) {
    layout[id] = { ...pos };
  }
  const taken = new Set(
    Object.values(layout).map((p) => `${Math.round(p.x)}:${Math.round(p.y)}`),
  );
  let slot = 0;
  for (const ci of items) {
    if (layout[ci.id]) continue;
    // Place orphan in free space; bump slightly if slot collides
    let pos = ORPHAN_SLOTS[slot % ORPHAN_SLOTS.length];
    let guard = 0;
    while (taken.has(`${pos.x}:${pos.y}`) && guard < 20) {
      slot += 1;
      const base = ORPHAN_SLOTS[slot % ORPHAN_SLOTS.length];
      const ring = Math.floor(slot / ORPHAN_SLOTS.length);
      pos = {
        x: Math.min(370, Math.max(30, base.x + (ring % 2 === 0 ? 18 : -18) * ring)),
        y: Math.min(255, Math.max(30, base.y + (ring % 3) * 12)),
      };
      guard += 1;
    }
    layout[ci.id] = { x: pos.x, y: pos.y, orphan: true };
    taken.add(`${pos.x}:${pos.y}`);
    slot += 1;
  }
  return layout;
}

/** BFS impact hops (1–2) from a root CI using live relations */
function computeImpactFromSelection(
  rootId: string,
  relations: CiRelation[],
  items: ConfigurationItem[],
): {
  rootCiId: string;
  entries: {
    ciId: string;
    hop: 1 | 2;
    impact: ImpactLevel;
    usersAffected?: number;
  }[];
} {
  const byId = new Map(items.map((c) => [c.id, c]));
  const adj = new Map<string, string[]>();
  for (const r of relations) {
    if (!adj.has(r.fromId)) adj.set(r.fromId, []);
    if (!adj.has(r.toId)) adj.set(r.toId, []);
    adj.get(r.fromId)!.push(r.toId);
    adj.get(r.toId)!.push(r.fromId);
  }

  const hopOf = new Map<string, 1 | 2>();
  const q: { id: string; hop: number }[] = [{ id: rootId, hop: 0 }];
  const seen = new Set<string>([rootId]);

  while (q.length) {
    const cur = q.shift()!;
    if (cur.hop >= 2) continue;
    for (const n of adj.get(cur.id) ?? []) {
      if (seen.has(n)) continue;
      seen.add(n);
      const hop = (cur.hop + 1) as 1 | 2;
      hopOf.set(n, hop);
      q.push({ id: n, hop });
    }
  }

  const critToImpact = (
    c?: ConfigurationItem['criticality'],
  ): ImpactLevel => {
    if (c === 'critical' || c === 'high') return 'high';
    if (c === 'medium') return 'medium';
    return 'low';
  };

  const usersFor = (ci: ConfigurationItem | undefined, hop: 1 | 2): number => {
    if (!ci) return 0;
    const base =
      ci.criticality === 'critical'
        ? 900
        : ci.criticality === 'high'
          ? 420
          : ci.criticality === 'medium'
            ? 180
            : 40;
    return hop === 1 ? base : Math.round(base * 0.55);
  };

  const entries = [...hopOf.entries()]
    .map(([ciId, hop]) => {
      const ci = byId.get(ciId);
      return {
        ciId,
        hop,
        impact: critToImpact(ci?.criticality),
        usersAffected: usersFor(ci, hop),
      };
    })
    .sort((a, b) => a.hop - b.hop || a.ciId.localeCompare(b.ciId));

  return { rootCiId: rootId, entries };
}

const KIND_OPTIONS = [
  'cmdb.kinds.linuxServer',
  'cmdb.kinds.businessService',
  'cmdb.kinds.networkDevice',
  'cmdb.kinds.database',
  'cmdb.kinds.application',
] as const;

type FilterId = 'all' | 'services' | 'infra' | 'apps';

function matchesFilter(ci: ConfigurationItem, filter: FilterId): boolean {
  if (filter === 'all') return true;
  if (filter === 'services') return ci.icon === 'cloud' || ci.icon === 'app';
  if (filter === 'infra')
    return (
      ci.icon === 'server' || ci.icon === 'network' || ci.icon === 'database'
    );
  if (filter === 'apps') return ci.icon === 'app';
  return true;
}

function neighborsOf(
  id: string,
  relations: CiRelation[],
): { ids: Set<string>; edges: Set<string> } {
  const ids = new Set<string>();
  const edges = new Set<string>();
  for (const r of relations) {
    if (r.fromId === id) {
      ids.add(r.toId);
      edges.add(r.id);
    } else if (r.toId === id) {
      ids.add(r.fromId);
      edges.add(r.id);
    }
  }
  return { ids, edges };
}

function impactTone(level: ImpactLevel): string {
  if (level === 'high') return 'high';
  if (level === 'medium') return 'medium';
  return 'low';
}

export function CmdbPage() {
  const t = useT();
  const { success, error: toastError } = useToast();
  const [searchParams] = useSearchParams();
  const ciFromQuery = searchParams.get('ci');
  const [filter, setFilter] = useState<FilterId>('all');
  const [q, setQ] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(ciFromQuery);
  const [showAdd, setShowAdd] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [showImpact, setShowImpact] = useState(false);
  const [localItems, setLocalItems] = useState<ConfigurationItem[] | null>(
    null,
  );
  const [localRelations, setLocalRelations] = useState<CiRelation[] | null>(
    null,
  );
  const [relTargetId, setRelTargetId] = useState('');
  const [relType, setRelType] = useState<CiRelationType>('depends_on');
  const [relBusy, setRelBusy] = useState(false);
  const [relError, setRelError] = useState<string | null>(null);
  const [editingRelId, setEditingRelId] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const detailRef = useRef<HTMLDivElement>(null);

  const itemsAsync = useAsync(() => fetchConfigurationItems(), []);
  const relationsAsync = useAsync(() => fetchCiRelations(), []);

  // Keep list in sync with session store (after Add CI / relation edit)
  useEffect(() => {
    if (itemsAsync.data) setLocalItems(itemsAsync.data);
  }, [itemsAsync.data]);

  useEffect(() => {
    if (relationsAsync.data) setLocalRelations(relationsAsync.data);
  }, [relationsAsync.data]);

  useEffect(() => {
    return subscribeConfigurationItems(() => {
      void fetchConfigurationItems().then(setLocalItems);
      void fetchCiRelations().then(setLocalRelations);
    });
  }, []);

  const data = localItems ?? itemsAsync.data;
  const loading = itemsAsync.loading && !data;
  const error = itemsAsync.error;
  const relations = useMemo(
    () => localRelations ?? relationsAsync.data ?? [],
    [localRelations, relationsAsync.data],
  );

  const filterCounts = useMemo(() => {
    const all = data ?? [];
    return {
      all: all.length,
      services: all.filter((c) => matchesFilter(c, 'services')).length,
      infra: all.filter((c) => matchesFilter(c, 'infra')).length,
      apps: all.filter((c) => matchesFilter(c, 'apps')).length,
    };
  }, [data]);

  const list = useMemo(() => {
    let items = data ?? [];
    items = items.filter((c) => matchesFilter(c, filter));
    if (q.trim()) {
      const needle = q.toLowerCase();
      items = items.filter(
        (c) =>
          c.name.toLowerCase().includes(needle) ||
          c.owner.toLowerCase().includes(needle) ||
          t(c.kindKey).toLowerCase().includes(needle),
      );
    }
    return items;
  }, [data, filter, q, t]);

  const selected = useMemo(
    () => (data ?? []).find((c) => c.id === selectedId) ?? null,
    [data, selectedId],
  );

  const { ids: neighborIds, edges: neighborEdges } = useMemo(
    () =>
      selectedId
        ? neighborsOf(selectedId, relations)
        : { ids: new Set<string>(), edges: new Set<string>() },
    [selectedId, relations],
  );

  const selectedRelations = useMemo(() => {
    if (!selectedId) return [];
    return relations
      .filter((r) => r.fromId === selectedId || r.toId === selectedId)
      .map((r) => {
        const otherId = r.fromId === selectedId ? r.toId : r.fromId;
        const other = (data ?? []).find((c) => c.id === otherId);
        const direction: 'out' | 'in' =
          r.fromId === selectedId ? 'out' : 'in';
        return { relation: r, other, direction };
      });
  }, [selectedId, relations, data]);

  const graphLayout = useMemo(
    () => buildGraphLayout(data ?? []),
    [data],
  );

  /** All CIs appear on the map (seed positions + free-space orphans) */
  const graphItems = useMemo(() => data ?? [], [data]);

  const liveStats = useMemo(() => {
    const all = data ?? [];
    const total = all.length;
    const operational = all.filter((c) => c.status === 'operational').length;
    const operationalPct =
      total === 0 ? 0 : Math.round((operational / total) * 1000) / 10;
    return {
      total,
      operational,
      operationalPct,
      relations: relations.length,
    };
  }, [data, relations]);

  /** Selection-aware impact: 1–2 hop neighbors of the focused CI */
  const selectionImpact = useMemo(() => {
    if (!selectedId) return null;
    return computeImpactFromSelection(selectedId, relations, data ?? []);
  }, [selectedId, relations, data]);

  const selectCi = useCallback((id: string) => {
    setSelectedId(id);
    setRelError(null);
    setRelTargetId('');
    setEditingRelId(null);
  }, []);

  /** Double-click / focus: select and bring detail panel into view */
  const focusCi = useCallback(
    (id: string) => {
      selectCi(id);
      // Defer scroll so selection paint runs first
      requestAnimationFrame(() => {
        detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        listRef.current
          ?.querySelector<HTMLElement>(`[data-ci-id="${id}"]`)
          ?.focus();
      });
    },
    [selectCi],
  );

  const exportCiCsv = useCallback(() => {
    const source = list.length ? list : (data ?? []);
    const stamp = new Date().toISOString().slice(0, 10);
    downloadCiListCsv(
      source,
      (key) => t(key),
      (s) => t(`status.${s}`),
      `itsm-cmdb-cis-${stamp}.csv`,
    );
    success(t('cmdb.exportDone', { n: source.length }));
  }, [list, data, t, success]);

  const targetCiOptions = useMemo(() => {
    if (!selectedId) return [];
    return (data ?? [])
      .filter((c) => c.id !== selectedId)
      .map((c) => ({ value: c.id, label: c.name }));
  }, [data, selectedId]);

  const handleAddRelation = async () => {
    if (!selectedId) return;
    if (!relTargetId) {
      setRelError(t('cmdb.relForm.required'));
      return;
    }
    setRelBusy(true);
    setRelError(null);
    try {
      const result = await createCiRelation({
        fromId: selectedId,
        toId: relTargetId,
        type: relType,
      });
      if (!result.ok) {
        setRelError(t(result.errorKey));
        toastError(t(result.errorKey));
        return;
      }
      success(t('cmdb.relForm.added'));
      setRelTargetId('');
      // store notify refreshes via subscribe; optimistic local push
      setLocalRelations((prev) => [...(prev ?? relations), result.relation]);
    } finally {
      setRelBusy(false);
    }
  };

  const handleRemoveRelation = async (id: string) => {
    setRelBusy(true);
    setRelError(null);
    try {
      const result = await deleteCiRelation(id);
      if (!result.ok) {
        setRelError(t(result.errorKey));
        toastError(t(result.errorKey));
        return;
      }
      success(t('cmdb.relForm.removed'));
      setEditingRelId(null);
      setLocalRelations((prev) =>
        (prev ?? relations).filter((r) => r.id !== id),
      );
    } finally {
      setRelBusy(false);
    }
  };

  const handleUpdateRelationType = async (
    id: string,
    nextType: CiRelationType,
  ) => {
    setRelBusy(true);
    setRelError(null);
    try {
      const result = await updateCiRelation(id, { type: nextType });
      if (!result.ok) {
        setRelError(t(result.errorKey));
        toastError(t(result.errorKey));
        return;
      }
      success(t('cmdb.relForm.updated'));
      setEditingRelId(null);
      setLocalRelations((prev) =>
        (prev ?? relations).map((r) =>
          r.id === id ? result.relation : r,
        ),
      );
    } finally {
      setRelBusy(false);
    }
  };

  const onListKeyDown = useCallback(
    (e: KeyboardEvent<HTMLDivElement>) => {
      if (!list.length) return;
      const idx = list.findIndex((c) => c.id === selectedId);
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const next = list[Math.min(list.length - 1, Math.max(0, idx) + 1)];
        if (next) setSelectedId(next.id);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const prev = list[Math.max(0, (idx < 0 ? 0 : idx) - 1)];
        if (prev) setSelectedId(prev.id);
      } else if (e.key === 'Home') {
        e.preventDefault();
        setSelectedId(list[0].id);
      } else if (e.key === 'End') {
        e.preventDefault();
        setSelectedId(list[list.length - 1].id);
      } else if (e.key === 'Enter' || e.key === ' ') {
        if (idx >= 0) e.preventDefault();
      }
    },
    [list, selectedId],
  );

  // Honor ?ci= deep-link, else default once data loads
  useEffect(() => {
    if (!data?.length) return;
    if (ciFromQuery && data.some((c) => c.id === ciFromQuery)) {
      setSelectedId(ciFromQuery);
      return;
    }
    if (!selectedId) {
      setSelectedId(data[0].id);
    }
  }, [data, selectedId, ciFromQuery]);

  const handleAdd = async (payload: {
    name: string;
    kindKey: string;
    status: CiStatus;
  }) => {
    try {
      const created = await createConfigurationItem(payload);
      setLocalItems((prev) => [created, ...(prev ?? data ?? [])]);
      setSelectedId(created.id);
      setShowAdd(false);
      success(t('cmdb.addSuccess', { name: created.name }));
    } catch {
      toastError(t('cmdb.addError'));
    }
  };

  const handleEdit = async (payload: {
    name: string;
    kindKey: string;
    status: CiStatus;
  }) => {
    if (!selected) return;
    try {
      const updated = await updateConfigurationItem(selected.id, {
        ...payload,
        expectedVersion: selected.version ?? 0,
        owner: selected.owner === 'вЂ”' ? '' : selected.owner,
      });
      setLocalItems((prev) =>
        (prev ?? data ?? []).map((ci) => (ci.id === updated.id ? updated : ci)),
      );
      setShowEdit(false);
      success(t('workItem.savedToast'));
    } catch {
      toastError(t('app.error'));
      void itemsAsync.reload();
    }
  };

  if (error && !loading && !data) {
    return (
      <section className="page page--cmdb">
        <div className="page-head">
          <div>
            <p className="eyebrow">{t('cmdb.kicker')}</p>
            <h1>{t('cmdb.title')}</h1>
          </div>
        </div>
        <ErrorState onRetry={itemsAsync.reload} />
      </section>
    );
  }

  const impactEntries = selectionImpact?.entries ?? [];
  const impactedUsers = impactEntries.reduce(
    (sum, e) => sum + (e.usersAffected ?? 0),
    0,
  );
  const impactRootName = selected?.name ?? t('cmdb.unknownCi');

  return (
    <section className="page page--cmdb">
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('cmdb.kicker')}</p>
          <h1>{t('cmdb.title')}</h1>
          <p className="page-subtitle">{t('cmdb.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <Button
            variant="secondary"
            size="sm"
            icon={<Download size={16} />}
            onClick={exportCiCsv}
            disabled={!data?.length}
          >
            {t('cmdb.exportCsv')}
          </Button>
          <Button
            variant="primary"
            icon={<Plus size={18} />}
            onClick={() => setShowAdd(true)}
          >
            {t('cmdb.addCi')}
          </Button>
        </div>
      </div>

      <div className="cmdb-stats">
        <div>
          <span>
            <Boxes size={15} />
          </span>
          <b>{liveStats.total.toLocaleString()}</b>
          <small>{t('cmdb.statItems')}</small>
          <em>{t('cmdb.statFromStore')}</em>
        </div>
        <div>
          <span>
            <CheckCircle2 size={15} />
          </span>
          <b>
            {liveStats.operationalPct % 1 === 0
              ? `${liveStats.operationalPct}%`
              : `${liveStats.operationalPct.toFixed(1)}%`}
          </b>
          <small>{t('cmdb.statOperational')}</small>
          <em>
            {t('cmdb.statOperationalDetail', {
              n: liveStats.operational,
              total: liveStats.total,
            })}
          </em>
        </div>
        <div>
          <span>
            <Network size={15} />
          </span>
          <b>{liveStats.relations.toLocaleString()}</b>
          <small>{t('cmdb.statRelations')}</small>
          <em>{t('cmdb.statGraph')}</em>
        </div>
      </div>

      <div className="cmdb-workspace">
        <section className="ci-panel">
          <div className="ci-head">
            <div>
              <h2>{t('cmdb.ciTitle')}</h2>
              <p>{t('cmdb.ciHint')}</p>
            </div>
            <label>
              <Search size={16} aria-hidden />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('cmdb.searchCi')}
                aria-label={t('cmdb.searchCi')}
              />
            </label>
          </div>
          <div className="ci-filters" role="tablist" aria-label={t('cmdb.ciTitle')}>
            {(
              [
                ['all', t('cmdb.filterAll'), filterCounts.all],
                ['services', t('cmdb.filterServices'), filterCounts.services],
                ['infra', t('cmdb.filterInfra'), filterCounts.infra],
                ['apps', t('cmdb.filterApps'), filterCounts.apps],
              ] as const
            ).map(([id, label, count]) => (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={filter === id}
                className={filter === id ? 'is-active' : undefined}
                onClick={() => setFilter(id)}
              >
                {label} <b>{count}</b>
              </button>
            ))}
          </div>

          {loading ? (
            <div className="ci-list-skeleton">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} height={48} radius={8} className="mb-2" />
              ))}
            </div>
          ) : list.length === 0 ? (
            <EmptyState
              title={t('cmdb.emptyTitle')}
              description={t('cmdb.emptyHint')}
              actionLabel={t('app.reset')}
              onAction={() => {
                setQ('');
                setFilter('all');
              }}
            />
          ) : (
            <div
              className="ci-list"
              ref={listRef}
              role="listbox"
              aria-label={t('cmdb.ciTitle')}
              tabIndex={0}
              onKeyDown={onListKeyDown}
            >
              {list.map((ci) => {
                const Icon = icons[ci.icon] ?? Server;
                const isSelected = ci.id === selectedId;
                return (
                  <button
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    className={`ci-row${isSelected ? ' is-selected' : ''}`}
                    key={ci.id}
                    data-ci-id={ci.id}
                    onClick={() => selectCi(ci.id)}
                    onDoubleClick={() => focusCi(ci.id)}
                  >
                    <span className={`ci-icon ci-icon--${ci.tone}`}>
                      <Icon size={16} />
                    </span>
                    <span className="ci-main">
                      <b>{ci.name}</b>
                      <small>
                        {t(ci.kindKey)} · {ci.owner}
                      </small>
                    </span>
                    <StatusChip status={ci.status} />
                    <ArrowRight size={16} aria-hidden />
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <aside className="map-panel">
          <div className="map-head">
            <div>
              <h2>{t('cmdb.mapTitle')}</h2>
              <p>
                {selected
                  ? t('cmdb.mapFocus', { name: selected.name })
                  : t('cmdb.mapService')}
              </p>
            </div>
          </div>

          {relationsAsync.loading && !relations.length ? (
            <div className="dependency-map dependency-map--loading">
              <Skeleton height={200} radius={0} />
            </div>
          ) : graphItems.length === 0 ? (
            <div className="dependency-map dependency-map--empty">
              <EmptyState
                title={t('cmdb.mapEmptyTitle')}
                description={t('cmdb.mapEmptyHint')}
              />
            </div>
          ) : (
            <DependencyGraph
              items={graphItems}
              allItems={data ?? []}
              relations={relations}
              layout={graphLayout}
              selectedId={selectedId}
              neighborIds={neighborIds}
              neighborEdges={neighborEdges}
              onSelect={selectCi}
              onFocus={focusCi}
              label={t('cmdb.mapTitle')}
            />
          )}

          <div
            className="map-footer map-footer--legend"
            role="list"
            aria-label={t('cmdb.healthLegend')}
          >
            <span role="listitem" className="map-footer__item">
              <i className="is-ok" aria-hidden />
              <span className="map-footer__label">
                {t('cmdb.healthy')} — {t('status.operational')}
              </span>
            </span>
            <span role="listitem" className="map-footer__item">
              <i className="is-warn" aria-hidden />
              <span className="map-footer__label">
                {t('cmdb.attention')} — {t('status.degraded')} /{' '}
                {t('status.maintenance')}
              </span>
            </span>
            <span role="listitem" className="map-footer__item">
              <i className="is-retired" aria-hidden />
              <span className="map-footer__label">
                {t('cmdb.retiredLegend')} — {t('status.retired')}
              </span>
            </span>
            <span className="map-footer__hint">{t('cmdb.mapHint')}</span>
          </div>

          <div className="ci-detail" ref={detailRef}>
            {selected ? (
              <>
                <div className="ci-detail__head">
                  <div>
                    <h3>{selected.name}</h3>
                    <p>{t(selected.kindKey)}</p>
                  </div>
                  <div>
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<Pencil size={14} />}
                      onClick={() => setShowEdit(true)}
                    >
                      {t('app.edit')}
                    </Button>
                    <StatusChip status={selected.status} />
                  </div>
                </div>
                <dl className="ci-detail__dl">
                  <div>
                    <dt>{t('cmdb.fieldOwner')}</dt>
                    <dd>{selected.owner}</dd>
                  </div>
                  {selected.environment && (
                    <div>
                      <dt>{t('cmdb.fieldEnvironment')}</dt>
                      <dd>{t(`cmdb.env.${selected.environment}`)}</dd>
                    </div>
                  )}
                  {selected.criticality && (
                    <div>
                      <dt>{t('cmdb.fieldCriticality')}</dt>
                      <dd>{t(`priority.${selected.criticality}`)}</dd>
                    </div>
                  )}
                </dl>
                <div className="ci-detail__rels">
                  <h4>{t('cmdb.relationships')}</h4>
                  {selectedRelations.length === 0 ? (
                    <p className="ci-detail__empty">{t('cmdb.noRelations')}</p>
                  ) : (
                    <ul>
                      {selectedRelations.map(({ relation, other, direction }) => {
                        const relTypeKey = displayRelType(relation.type);
                        const isEditing = editingRelId === relation.id;
                        return (
                          <li key={relation.id} className="ci-rel-row">
                            {isEditing ? (
                              <div className="ci-rel-edit">
                                <Select
                                  label={t('cmdb.relForm.type')}
                                  value={relTypeKey}
                                  onChange={(e) =>
                                    void handleUpdateRelationType(
                                      relation.id,
                                      e.target.value as CiRelationType,
                                    )
                                  }
                                  options={EDITABLE_REL_TYPES.map((rt) => ({
                                    value: rt,
                                    label: t(`cmdb.rel.${rt}`),
                                  }))}
                                  disabled={relBusy}
                                />
                                <button
                                  type="button"
                                  className="text-button"
                                  disabled={relBusy}
                                  onClick={() => setEditingRelId(null)}
                                >
                                  {t('app.cancel')}
                                </button>
                              </div>
                            ) : (
                              <button
                                type="button"
                                className="ci-rel"
                                disabled={!other}
                                onClick={() => other && selectCi(other.id)}
                                onDoubleClick={(e) => {
                                  e.preventDefault();
                                  setEditingRelId(relation.id);
                                }}
                                title={t('cmdb.relForm.editHint')}
                              >
                                <span className="ci-rel__type">
                                  {direction === 'out'
                                    ? t(`cmdb.rel.${relTypeKey}`)
                                    : t(`cmdb.relIn.${relTypeKey}`)}
                                </span>
                                <span className="ci-rel__name">
                                  {other?.name ?? t('cmdb.unknownCi')}
                                </span>
                                {other && <StatusChip status={other.status} />}
                              </button>
                            )}
                            <div className="ci-rel-row__actions">
                              {!isEditing && (
                                <button
                                  type="button"
                                  className="text-button ci-rel__edit"
                                  disabled={relBusy}
                                  onClick={() => setEditingRelId(relation.id)}
                                >
                                  {t('cmdb.relForm.editType')}
                                </button>
                              )}
                              <button
                                type="button"
                                className="ci-rel__remove icon-btn"
                                aria-label={t('cmdb.relForm.remove')}
                                disabled={relBusy}
                                onClick={() =>
                                  void handleRemoveRelation(relation.id)
                                }
                              >
                                <Trash2 size={14} />
                              </button>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                  <form
                    className="ci-rel-form"
                    onSubmit={(e) => {
                      e.preventDefault();
                      void handleAddRelation();
                    }}
                  >
                    <h5 className="ci-rel-form__title">{t('cmdb.relForm.title')}</h5>
                    <Select
                      label={t('cmdb.relForm.target')}
                      value={relTargetId}
                      onChange={(e) => setRelTargetId(e.target.value)}
                      options={[
                        { value: '', label: t('cmdb.relForm.targetPlaceholder') },
                        ...targetCiOptions,
                      ]}
                    />
                    <Select
                      label={t('cmdb.relForm.type')}
                      value={relType}
                      onChange={(e) =>
                        setRelType(e.target.value as CiRelationType)
                      }
                      options={EDITABLE_REL_TYPES.map((rt) => ({
                        value: rt,
                        label: t(`cmdb.rel.${rt}`),
                      }))}
                    />
                    {relError && (
                      <p className="field__error" role="alert">
                        {relError}
                      </p>
                    )}
                    <Button
                      type="submit"
                      size="sm"
                      variant="secondary"
                      disabled={relBusy || !relTargetId}
                      icon={<Plus size={14} />}
                    >
                      {t('cmdb.relForm.add')}
                    </Button>
                  </form>
                </div>
              </>
            ) : (
              <p className="ci-detail__empty">{t('cmdb.selectCiHint')}</p>
            )}
          </div>
        </aside>
      </div>

      <div className="impact-strip">
        <span className="impact-icon">
          <Sparkles size={18} />
        </span>
        <div>
          <b>
            {selected
              ? t('cmdb.impactReadyFor', { name: selected.name })
              : t('cmdb.impactReady')}
          </b>
          <p>
            {selected
              ? impactEntries.length > 0
                ? t('cmdb.impactTextForCi', {
                    name: impactRootName,
                    services: impactEntries.length,
                    users: impactedUsers.toLocaleString(),
                  })
                : t('cmdb.impactTextNoNeighbors', { name: impactRootName })
              : t('cmdb.impactSelectHint')}
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowImpact(true)}
          disabled={!selected}
        >
          {t('cmdb.openImpact')} <ArrowRight size={15} />
        </button>
      </div>

      {showAdd && (
        <AddCiModal onClose={() => setShowAdd(false)} onSubmit={handleAdd} />
      )}

      {showEdit && selected && (
        <AddCiModal
          initial={selected}
          onClose={() => setShowEdit(false)}
          onSubmit={handleEdit}
        />
      )}

      {showImpact && selected && (
        <ImpactPanel
          onClose={() => setShowImpact(false)}
          loading={false}
          error={null}
          onRetry={() => undefined}
          scenarioTitle={t('cmdb.impactScenarioFor', { name: selected.name })}
          rootCiId={selected.id}
          entries={impactEntries}
          items={data ?? []}
          onSelectCi={(id) => {
            setSelectedId(id);
            setShowImpact(false);
          }}
        />
      )}
    </section>
  );
}

function DependencyGraph({
  items,
  allItems,
  relations,
  layout,
  selectedId,
  neighborIds,
  neighborEdges,
  onSelect,
  onFocus,
  label,
}: {
  items: ConfigurationItem[];
  allItems: ConfigurationItem[];
  relations: CiRelation[];
  layout: Record<string, { x: number; y: number; orphan?: boolean }>;
  selectedId: string | null;
  neighborIds: Set<string>;
  neighborEdges: Set<string>;
  onSelect: (id: string) => void;
  /** Double-click focuses CI detail (scroll + select) */
  onFocus: (id: string) => void;
  label: string;
}) {
  const t = useT();
  const byId = useMemo(
    () => new Map(allItems.map((c) => [c.id, c])),
    [allItems],
  );

  const edges = relations.filter((r) => layout[r.fromId] && layout[r.toId]);

  return (
    <div className="dependency-map dependency-map--svg" role="group" aria-label={label}>
      <svg
        viewBox="0 0 400 280"
        className="dep-svg"
        role="img"
        aria-label={label}
      >
        <defs>
          <marker
            id="dep-arrow"
            markerWidth="8"
            markerHeight="8"
            refX="6"
            refY="3"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path d="M0,0 L6,3 L0,6 Z" fill="#a8aec4" />
          </marker>
          <marker
            id="dep-arrow-hot"
            markerWidth="8"
            markerHeight="8"
            refX="6"
            refY="3"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path d="M0,0 L6,3 L0,6 Z" fill="#7158df" />
          </marker>
        </defs>
        {/* soft grid */}
        {Array.from({ length: 14 }).map((_, i) => (
          <line
            key={`vg-${i}`}
            x1={i * 30}
            y1={0}
            x2={i * 30}
            y2={280}
            className="dep-svg__grid"
          />
        ))}
        {Array.from({ length: 10 }).map((_, i) => (
          <line
            key={`hg-${i}`}
            x1={0}
            y1={i * 30}
            x2={400}
            y2={i * 30}
            className="dep-svg__grid"
          />
        ))}

        {edges.map((r) => {
          const a = layout[r.fromId];
          const b = layout[r.toId];
          if (!a || !b) return null;
          const hot =
            neighborEdges.has(r.id) ||
            r.fromId === selectedId ||
            r.toId === selectedId;
          const dim = selectedId && !hot;
          // shorten line toward node centers so arrows don't sit under nodes
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const len = Math.hypot(dx, dy) || 1;
          const pad = 28;
          const x1 = a.x + (dx / len) * pad;
          const y1 = a.y + (dy / len) * pad;
          const x2 = b.x - (dx / len) * pad;
          const y2 = b.y - (dy / len) * pad;
          return (
            <line
              key={r.id}
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              className={`dep-svg__edge${hot ? ' is-hot' : ''}${dim ? ' is-dim' : ''}`}
              markerEnd={hot ? 'url(#dep-arrow-hot)' : 'url(#dep-arrow)'}
            />
          );
        })}

        {items.map((ci) => {
          const pos = layout[ci.id];
          if (!pos) return null;
          const isSel = ci.id === selectedId;
          const isNb = neighborIds.has(ci.id);
          const isOrphan = Boolean(pos.orphan);
          const dim = selectedId && !isSel && !isNb;
          const warn = ci.status === 'degraded' || ci.status === 'maintenance';
          return (
            <g
              key={ci.id}
              className={`dep-svg__node${isSel ? ' is-selected' : ''}${isNb ? ' is-neighbor' : ''}${dim ? ' is-dim' : ''}${warn ? ' is-warn' : ''}${isOrphan ? ' is-orphan' : ''}`}
              transform={`translate(${pos.x}, ${pos.y})`}
              role="button"
              tabIndex={0}
              aria-label={
                isOrphan
                  ? `${ci.name} (${t('cmdb.orphanNode')})`
                  : ci.name
              }
              aria-pressed={isSel}
              onClick={() => onSelect(ci.id)}
              onDoubleClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                onFocus(ci.id);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelect(ci.id);
                }
              }}
            >
              <title>
                {ci.name} — {t(ci.kindKey)} ({t(`status.${ci.status}`)})
                {isOrphan ? ` · ${t('cmdb.orphanNode')}` : ''}
              </title>
              <rect
                x={-52}
                y={-22}
                width={104}
                height={44}
                rx={8}
                className="dep-svg__card"
              />
              <circle
                cx={-36}
                cy={0}
                r={5}
                className={`dep-svg__dot${warn ? ' is-warn' : isOrphan ? ' is-orphan' : ' is-ok'}`}
              />
              <text x={-24} y={-2} className="dep-svg__label">
                {ci.name.length > 14 ? `${ci.name.slice(0, 13)}…` : ci.name}
              </text>
              <text x={-24} y={12} className="dep-svg__sub">
                {isOrphan
                  ? t('cmdb.orphanNode')
                  : t(ci.kindKey).length > 16
                    ? `${t(ci.kindKey).slice(0, 15)}…`
                    : t(ci.kindKey)}
              </text>
            </g>
          );
        })}
      </svg>
      <ul className="sr-only">
        {items.map((ci) => (
          <li key={ci.id}>
            {ci.name}
            {byId.get(ci.id)?.status
              ? ` — ${t(`status.${byId.get(ci.id)!.status}`)}`
              : ''}
            {layout[ci.id]?.orphan ? ` — ${t('cmdb.orphanNode')}` : ''}
          </li>
        ))}
      </ul>
    </div>
  );
}

function AddCiModal({
  onClose,
  onSubmit,
  initial,
}: {
  onClose: () => void;
  initial?: ConfigurationItem;
  onSubmit: (payload: {
    name: string;
    kindKey: string;
    status: CiStatus;
  }) => Promise<void>;
}) {
  const t = useT();
  const [name, setName] = useState(initial?.name ?? '');
  const [kindKey, setKindKey] = useState<string>(initial?.kindKey ?? KIND_OPTIONS[0]);
  const [status, setStatus] = useState<CiStatus>(initial?.status ?? 'operational');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const submit = async () => {
    if (!name.trim()) {
      setErr(t('cmdb.formNameRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await onSubmit({ name: name.trim(), kindKey, status });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={initial ? `${t('app.edit')}: ${initial.name}` : t('cmdb.addCiTitle')}
      labelledBy="add-ci-title"
    >
      <div className="cmdb-add-form">
        <Input
          label={t('cmdb.formName')}
          name="ci-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          autoFocus
          error={err || undefined}
          placeholder={t('cmdb.formNamePlaceholder')}
        />
        <Select
          label={t('cmdb.formClass')}
          name="ci-class"
          value={kindKey}
          onChange={(e) => setKindKey(e.target.value)}
          options={KIND_OPTIONS.map((k) => ({ value: k, label: t(k) }))}
        />
        <Select
          label={t('cmdb.formStatus')}
          name="ci-status"
          value={status}
          onChange={(e) => setStatus(e.target.value as CiStatus)}
          options={[
            { value: 'operational', label: t('status.operational') },
            { value: 'degraded', label: t('status.degraded') },
            { value: 'maintenance', label: t('status.maintenance') },
            { value: 'retired', label: t('status.retired') },
          ]}
        />
        <div className="cmdb-add-form__actions">
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            {t('app.cancel')}
          </Button>
          <Button variant="primary" onClick={() => void submit()} disabled={busy}>
            {busy ? t('app.saving') : initial ? t('app.save') : t('cmdb.formSubmit')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function ImpactPanel({
  onClose,
  loading,
  error,
  onRetry,
  scenarioTitle,
  rootCiId,
  entries,
  items,
  onSelectCi,
}: {
  onClose: () => void;
  loading: boolean;
  error: Error | null;
  onRetry: () => void;
  /** Human title: scenario for the selected CI */
  scenarioTitle: string;
  rootCiId?: string;
  entries: {
    ciId: string;
    hop: 1 | 2;
    impact: ImpactLevel;
    usersAffected?: number;
    serviceKey?: string;
  }[];
  items: ConfigurationItem[];
  onSelectCi: (id: string) => void;
}) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref, true);

  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  const root = items.find((c) => c.id === rootCiId);
  const sorted = [...entries].sort((a, b) => a.hop - b.hop);

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
        className="service-drawer module-detail-drawer impact-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="impact-title"
      >
        <div className="service-drawer__head">
          <p className="eyebrow">{t('cmdb.impactKicker')}</p>
          <button
            type="button"
            className="icon-btn"
            aria-label={t('app.close')}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        <h2 id="impact-title">{t('cmdb.impactTitle')}</h2>
        <p className="impact-drawer__change">{scenarioTitle}</p>
        {root && (
          <p className="impact-drawer__root">
            {t('cmdb.impactRoot')}: <b>{root.name}</b>
          </p>
        )}
        <p className="impact-drawer__note">{t('cmdb.impactSelectionNote')}</p>

        {loading && !entries.length ? (
          <div className="impact-drawer__loading">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} height={56} radius={8} className="mb-2" />
            ))}
          </div>
        ) : error && !entries.length ? (
          <ErrorState onRetry={onRetry} />
        ) : sorted.length === 0 ? (
          <EmptyState
            title={t('cmdb.impactEmptyTitle')}
            description={t('cmdb.impactEmptyOrphanHint')}
          />
        ) : (
          <ul className="impact-list">
            {sorted.map((entry) => {
              const ci = items.find((c) => c.id === entry.ciId);
              return (
                <li key={`${entry.ciId}-${entry.hop}`}>
                  <button
                    type="button"
                    className={`impact-row impact-row--${impactTone(entry.impact)}`}
                    onClick={() => onSelectCi(entry.ciId)}
                    disabled={!ci}
                  >
                    <span className="impact-row__hop">
                      {t('cmdb.impactHop', { n: entry.hop })}
                    </span>
                    <span className="impact-row__main">
                      <b>{ci?.name ?? t('cmdb.unknownCi')}</b>
                      <small>
                        {entry.serviceKey
                          ? t(entry.serviceKey)
                          : ci
                            ? t(ci.kindKey)
                            : '—'}
                      </small>
                    </span>
                    <span className="impact-row__meta">
                      <span className={`impact-badge impact-badge--${entry.impact}`}>
                        {t(`priority.${entry.impact}`)}
                      </span>
                      {typeof entry.usersAffected === 'number' &&
                        entry.usersAffected > 0 && (
                          <small>
                            {t('cmdb.impactUsers', {
                              n: entry.usersAffected.toLocaleString(),
                            })}
                          </small>
                        )}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}

        <div className="impact-drawer__foot">
          <Button variant="secondary" onClick={onClose}>
            {t('app.close')}
          </Button>
        </div>
      </aside>
    </div>
  );
}
