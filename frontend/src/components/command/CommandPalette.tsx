import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertOctagon,
  BookOpen,
  Boxes,
  Bot,
  ClipboardList,
  Database,
  FilePlus2,
  Gauge,
  GitBranch,
  Grid2X2,
  LayoutDashboard,
  Package,
  Plus,
  Search,
  Settings,
  TicketCheck,
  AlertTriangle,
  ScrollText,
  Shield,
  Timer,
  Workflow,
  Zap,
} from 'lucide-react';
import { useT } from '@/i18n';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { fetchWorkItems, searchAll, searchHitPath, isMockMode } from '@/api';
import type { SearchHit } from '@/api';
import type { CreateKind, WorkItem } from '@/types';
import { PriorityBadge } from '@/components/data-display';

const RECENT_KEY = 'vox-cmd-recent';
const MAX_RECENT = 6;
const LIVE_SEARCH_MIN = 2;

type CmdKind = 'nav' | 'action' | 'workitem' | 'recent' | 'search';

interface CmdItem {
  id: string;
  kind: CmdKind;
  label: string;
  hint?: string;
  icon: typeof Search;
  run: () => void;
  keywords?: string;
  objectType?: string;
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  onCreate: (kind: CreateKind) => void;
}

function readRecent(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as string[];
    return Array.isArray(parsed) ? parsed.slice(0, MAX_RECENT) : [];
  } catch {
    return [];
  }
}

function pushRecent(id: string) {
  try {
    const next = [id, ...readRecent().filter((x) => x !== id)].slice(0, MAX_RECENT);
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    /* ignore */
  }
}

export function CommandPalette({ open, onClose, onCreate }: CommandPaletteProps) {
  const t = useT();
  const navigate = useNavigate();
  const panelRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [items, setItems] = useState<WorkItem[]>([]);
  const [recentIds, setRecentIds] = useState<string[]>([]);
  const [liveHits, setLiveHits] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);

  useFocusTrap(panelRef, open);

  useEffect(() => {
    if (!open) return;
    setQuery('');
    setActive(0);
    setLiveHits([]);
    setSearching(false);
    setRecentIds(readRecent());
    let cancelled = false;
    // Local list always available for empty-state / mock filtering
    fetchWorkItems().then((list) => {
      if (!cancelled) setItems(list);
    });
    const tmr = window.setTimeout(() => inputRef.current?.focus(), 20);
    return () => {
      cancelled = true;
      window.clearTimeout(tmr);
    };
  }, [open]);

  // Live: GET /api/v1/search?q= when query length >= 2. Mock: local filter only (below).
  useEffect(() => {
    if (!open) return;
    const q = query.trim();
    if (isMockMode() || q.length < LIVE_SEARCH_MIN) {
      setLiveHits([]);
      setSearching(false);
      return;
    }

    let cancelled = false;
    const controller = new AbortController();
    const tmr = window.setTimeout(() => {
      setSearching(true);
      searchAll(q, { limit: 16, signal: controller.signal })
        .then((hits) => {
          if (!cancelled) setLiveHits(hits);
        })
        .catch(() => {
          if (!cancelled) setLiveHits([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, 220);

    return () => {
      cancelled = true;
      controller.abort();
      window.clearTimeout(tmr);
    };
  }, [query, open]);

  const go = useCallback(
    (path: string, recentId?: string) => {
      if (recentId) pushRecent(recentId);
      navigate(path);
      onClose();
    },
    [navigate, onClose],
  );

  const create = useCallback(
    (kind: CreateKind) => {
      onClose();
      onCreate(kind);
    },
    [onClose, onCreate],
  );

  const staticCommands = useMemo<CmdItem[]>(() => {
    const nav: CmdItem[] = [
      {
        id: 'nav-overview',
        kind: 'nav',
        label: t('nav.overview'),
        hint: t('command.navigate'),
        icon: LayoutDashboard,
        run: () => go('/'),
        keywords: 'home dashboard',
      },
      {
        id: 'nav-mywork',
        kind: 'nav',
        label: t('nav.myWork'),
        hint: t('command.navigate'),
        icon: TicketCheck,
        run: () => go('/my-work'),
      },
      {
        id: 'nav-queues',
        kind: 'nav',
        label: t('nav.queues'),
        hint: t('command.navigate'),
        icon: Grid2X2,
        run: () => go('/queues'),
      },
      {
        id: 'nav-catalog',
        kind: 'nav',
        label: t('nav.catalog'),
        hint: t('command.navigate'),
        icon: ClipboardList,
        run: () => go('/catalog'),
      },
      {
        id: 'nav-knowledge',
        kind: 'nav',
        label: t('nav.knowledge'),
        hint: t('command.navigate'),
        icon: BookOpen,
        run: () => go('/knowledge'),
      },
      {
        id: 'nav-cmdb',
        kind: 'nav',
        label: t('nav.cmdb'),
        hint: t('command.navigate'),
        icon: Boxes,
        run: () => go('/cmdb'),
      },
      {
        id: 'nav-assets',
        kind: 'nav',
        label: t('nav.assets'),
        hint: t('command.navigate'),
        icon: Package,
        run: () => go('/assets'),
      },
      {
        id: 'nav-problems',
        kind: 'nav',
        label: t('nav.problems'),
        hint: t('command.navigate'),
        icon: AlertOctagon,
        run: () => go('/problems'),
      },
      {
        id: 'nav-changes',
        kind: 'nav',
        label: t('nav.changes'),
        hint: t('command.navigate'),
        icon: GitBranch,
        run: () => go('/changes'),
      },
      {
        id: 'nav-reports',
        kind: 'nav',
        label: t('nav.reports'),
        hint: t('command.navigate'),
        icon: Gauge,
        run: () => go('/reports'),
        keywords: 'reports analytics metrics',
      },
      {
        id: 'nav-metadata',
        kind: 'nav',
        label: t('nav.metadata'),
        hint: t('command.navigate'),
        icon: Database,
        run: () => go('/admin/metadata'),
        keywords: 'metadata objects attributes admin schema',
      },
      {
        id: 'nav-automation',
        kind: 'nav',
        label: t('nav.automation'),
        hint: t('command.navigate'),
        icon: Zap,
        run: () => go('/admin/automation'),
        keywords: 'automation rules events triggers actions when if then admin',
      },
      {
        id: 'nav-workflow',
        kind: 'nav',
        label: t('nav.workflow'),
        hint: t('command.navigate'),
        icon: Workflow,
        run: () => go('/admin/workflow'),
        keywords: 'workflow states transitions lifecycle version admin',
      },
      {
        id: 'nav-sla',
        kind: 'nav',
        label: t('nav.sla'),
        hint: t('command.navigate'),
        icon: Timer,
        run: () => go('/admin/sla'),
        keywords: 'sla policy response resolution calendar hours targets admin',
      },
      {
        id: 'nav-rbac',
        kind: 'nav',
        label: t('nav.rbac'),
        hint: t('command.navigate'),
        icon: Shield,
        run: () => go('/admin/rbac'),
        keywords: 'rbac roles permissions users access control admin security',
      },
      {
        id: 'nav-audit',
        kind: 'nav',
        label: t('nav.audit'),
        hint: t('command.navigate'),
        icon: ScrollText,
        run: () => go('/admin/audit'),
        keywords: 'audit trail log history events security admin',
      },
      {
        id: 'nav-search',
        kind: 'nav',
        label: t('nav.search'),
        hint: t('command.navigate'),
        icon: Search,
        run: () => go('/search'),
        keywords: 'search find global full-text',
      },
      {
        id: 'nav-settings',
        kind: 'nav',
        label: t('nav.settings'),
        hint: t('command.navigate'),
        icon: Settings,
        run: () => go('/settings'),
      },
    ];

    const actions: CmdItem[] = [
      {
        id: 'act-incident',
        kind: 'action',
        label: t('header.createIncident'),
        hint: t('header.createIncidentHint'),
        icon: AlertTriangle,
        run: () => create('incident'),
        keywords: 'create incident new',
      },
      {
        id: 'act-request',
        kind: 'action',
        label: t('header.createRequest'),
        hint: t('header.createRequestHint'),
        icon: FilePlus2,
        run: () => create('request'),
        keywords: 'create request new',
      },
      {
        id: 'act-copilot',
        kind: 'action',
        label: t('command.askCopilot'),
        hint: t('command.askCopilotHint'),
        icon: Bot,
        run: () => go('/?copilot=1'),
        keywords: 'copilot ai assistant summarize brief ask',
      },
    ];

    return [...actions, ...nav];
  }, [t, go, create]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    const rawQ = query.trim();
    const liveMode = !isMockMode();

    const workCmds: CmdItem[] = items.map((w) => ({
      id: `wi-${w.id}`,
      kind: 'workitem' as const,
      label: `${w.number} · ${w.title}`,
      hint: t(`priority.${w.priority}`),
      icon: TicketCheck,
      run: () => go(`/work-items/${w.id}`, `wi-${w.id}`),
      keywords: `${w.number} ${w.title} ${w.service} ${w.type} ${w.queue ?? ''}`,
      objectType: 'work-item',
    }));

    const searchCmds: CmdItem[] = liveHits.map((hit) => {
      const path = searchHitPath(hit);
      const id = `search-${hit.objectType}-${hit.id}`;
      return {
        id,
        kind: 'search' as const,
        label: hit.title || hit.id,
        hint: hit.body?.slice(0, 80) || undefined,
        icon: TicketCheck,
        objectType: hit.objectType || 'result',
        run: () => {
          if (path) {
            go(path, id);
          } else {
            go(`/work-items/${hit.id}`, id);
          }
        },
        keywords: `${hit.title} ${hit.body ?? ''} ${hit.objectType}`,
      };
    });

    const match = (cmd: CmdItem) => {
      if (!q) return true;
      const hay = `${cmd.label} ${cmd.hint ?? ''} ${cmd.keywords ?? ''}`.toLowerCase();
      return hay.includes(q);
    };

    const searchAllCmd: CmdItem | null = rawQ
      ? {
          id: 'act-search-all',
          kind: 'action' as const,
          label: t('command.searchAll', { q: rawQ }),
          hint: t('command.searchAllHint'),
          icon: Search,
          run: () => go(`/search?q=${encodeURIComponent(rawQ)}`),
          keywords: `search all full ${rawQ}`,
        }
      : null;

    if (!q) {
      const recent = recentIds
        .map((id) => workCmds.find((c) => c.id === id) || staticCommands.find((c) => c.id === id))
        .filter(Boolean) as CmdItem[];
      const recentMarked = recent.map((c) => ({ ...c, kind: 'recent' as CmdKind }));
      return {
        recent: recentMarked,
        actions: staticCommands.filter((c) => c.kind === 'action'),
        nav: staticCommands.filter((c) => c.kind === 'nav'),
        work: workCmds.slice(0, 8),
        search: [] as CmdItem[],
        searchAll: null as CmdItem | null,
      };
    }

    // Live + q>=2: platform search hits (with objectType badge). Mock: local work filter.
    const useLiveSearch = liveMode && q.length >= LIVE_SEARCH_MIN;

    return {
      recent: [] as CmdItem[],
      actions: staticCommands.filter((c) => c.kind === 'action' && match(c)),
      nav: staticCommands.filter((c) => c.kind === 'nav' && match(c)),
      work: useLiveSearch ? [] : workCmds.filter(match).slice(0, 12),
      search: useLiveSearch ? searchCmds.slice(0, 12) : [],
      searchAll: searchAllCmd,
    };
  }, [query, items, staticCommands, recentIds, liveHits, t, go]);

  const flat = useMemo(() => {
    const list: CmdItem[] = [];
    if (results.searchAll) list.push(results.searchAll);
    if (results.recent.length) list.push(...results.recent);
    list.push(...results.actions, ...results.nav, ...results.search, ...results.work);
    return list;
  }, [results]);

  useEffect(() => {
    setActive(0);
  }, [query, open, liveHits]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setActive((i) => Math.min(i + 1, Math.max(flat.length - 1, 0)));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setActive((i) => Math.max(i - 1, 0));
      } else if (e.key === 'Enter') {
        e.preventDefault();
        const item = flat[active];
        const rawQ = query.trim();
        // Prefer selected item; if none, open full search for the typed query
        if (item) {
          pushRecent(item.id);
          item.run();
        } else if (rawQ) {
          navigate(`/search?q=${encodeURIComponent(rawQ)}`);
          onClose();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, flat, active, onClose, query, navigate]);

  if (!open) return null;

  const groups: { key: string; title: string; items: CmdItem[] }[] = [
    {
      key: 'searchAll',
      title: t('command.search'),
      items: results.searchAll ? [results.searchAll] : [],
    },
    { key: 'recent', title: t('command.recent'), items: results.recent },
    { key: 'actions', title: t('command.actions'), items: results.actions },
    { key: 'nav', title: t('command.pages'), items: results.nav },
    { key: 'search', title: t('command.searchResults'), items: results.search },
    { key: 'work', title: t('command.workItems'), items: results.work },
  ].filter((g) => g.items.length > 0);

  let runningIndex = -1;
  const qLen = query.trim().length;

  return (
    <div
      className="cmd-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        className="cmd-palette"
        role="dialog"
        aria-modal="true"
        aria-label={t('command.title')}
      >
        <div className="cmd-palette__search">
          <Search size={18} aria-hidden />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('command.placeholder')}
            aria-label={t('command.placeholder')}
            aria-controls="cmd-list"
            aria-activedescendant={flat[active] ? `cmd-${flat[active].id}` : undefined}
            autoComplete="off"
            spellCheck={false}
          />
          {searching && qLen >= LIVE_SEARCH_MIN ? (
            <span className="cmd-palette__searching" aria-live="polite">
              {t('command.searching')}
            </span>
          ) : (
            <kbd>Esc</kbd>
          )}
        </div>

        <div className="cmd-palette__body" id="cmd-list" role="listbox">
          {flat.length === 0 ? (
            <div className="cmd-palette__empty">
              <p>{t('command.empty')}</p>
              <small>{t('command.emptyHint')}</small>
            </div>
          ) : (
            groups.map((group) => (
              <div key={group.key} className="cmd-group">
                <div className="cmd-group__title">{group.title}</div>
                {group.items.map((item) => {
                  runningIndex += 1;
                  const idx = runningIndex;
                  const Icon = item.icon;
                  const wi = items.find((w) => `wi-${w.id}` === item.id);
                  return (
                    <button
                      key={item.id}
                      id={`cmd-${item.id}`}
                      type="button"
                      role="option"
                      aria-selected={idx === active}
                      className={`cmd-item${idx === active ? ' is-active' : ''}`}
                      onMouseEnter={() => setActive(idx)}
                      onClick={() => {
                        pushRecent(item.id);
                        item.run();
                      }}
                    >
                      <span className={`cmd-item__icon cmd-item__icon--${item.kind}`}>
                        {item.kind === 'action' && item.id === 'act-incident' ? (
                          <Plus size={15} />
                        ) : (
                          <Icon size={15} />
                        )}
                      </span>
                      <span className="cmd-item__text">
                        <b>{item.label}</b>
                        {item.hint && <small>{item.hint}</small>}
                      </span>
                      {item.objectType && (item.kind === 'search' || item.kind === 'workitem') && (
                        <span className="cmd-item__type-badge" title={item.objectType}>
                          {item.objectType}
                        </span>
                      )}
                      {wi && <PriorityBadge priority={wi.priority} />}
                      {item.kind === 'action' && (
                        <span className="cmd-item__badge">{t('command.create')}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>

        <div className="cmd-palette__footer">
          <span>
            <kbd>↑</kbd>
            <kbd>↓</kbd>
            {t('command.navigateHint')}
          </span>
          <span>
            <kbd>↵</kbd>
            {t('command.openHint')}
          </span>
          <span>
            <kbd>esc</kbd>
            {t('app.close')}
          </span>
        </div>
      </div>
    </div>
  );
}

/** Global ⌘K / Ctrl+K listener — call from shell */
export function useCommandPaletteHotkey(open: () => void) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        open();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);
}
