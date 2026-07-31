import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  CheckCircle2,
  Clock3,
  FileText,
  KeyRound,
  Laptop,
  Pencil,
  Printer,
  Search,
  Shield,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import {
  createKnowledgeArticle,
  createWorkItem,
  fetchKnowledgeArticles,
  fetchKnowledgeTopics,
  isLiveFeatureUnsupported,
  publishKnowledgeArticle,
  readKnowledgeVote,
  submitKnowledgeVote,
  subscribeKnowledge,
  updateKnowledgeArticle,
} from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Skeleton,
  Tabs,
  Textarea,
} from '@/components/ui';
import type { KnowledgeArticle, KnowledgeArticleStatus } from '@/types';
import { formatDateTime } from '@/lib/format';

const articleIcons = {
  key: KeyRound,
  shield: Shield,
  laptop: Laptop,
  book: BookOpen,
} as const;

type KnowledgeTab = 'recommended' | 'popular' | 'updated';
type StatusFilter = 'all' | KnowledgeArticleStatus;

function articleTitle(a: KnowledgeArticle, t: (k: string) => string): string {
  if (a.title?.trim()) return a.title;
  return t(a.titleKey);
}

function articleSummary(a: KnowledgeArticle, t: (k: string) => string): string {
  if (a.summary?.trim()) return a.summary;
  return t(a.summaryKey);
}

function articleTag(a: KnowledgeArticle, t: (k: string) => string): string {
  if (a.tag?.trim()) return a.tag;
  if (a.title?.trim() && a.tagKey === 'knowledge.articles.contributed.tag') {
    return t('knowledge.articles.contributed.tag');
  }
  return t(a.tagKey);
}

function articleBody(a: KnowledgeArticle, t: (k: string) => string): string {
  if (a.body?.trim()) return a.body;
  const bodyKey = a.titleKey.replace('.title', '.body');
  const body = t(bodyKey);
  if (body === bodyKey) {
    return `${articleSummary(a, t)}\n\n${t('knowledge.articleBodyFallback')}`;
  }
  return body;
}

export function KnowledgePage() {
  const t = useT();
  const { success } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const articleFromQuery = searchParams.get('article');
  const [tab, setTab] = useState<KnowledgeTab>('recommended');
  const [query, setQuery] = useState('');
  const [topicId, setTopicId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [active, setActive] = useState<KnowledgeArticle | null>(null);
  const [contributeOpen, setContributeOpen] = useState(false);
  // Backend CMS write path live (create/update/publish)
  const cmsWritable = true;
  const articles = useAsync(() => fetchKnowledgeArticles(), []);
  const topics = useAsync(() => fetchKnowledgeTopics(), []);

  useEffect(() => {
    return subscribeKnowledge(() => {
      articles.reload();
    });
  }, [articles.reload]);

  // Honor ?article= deep-link from search / related links
  useEffect(() => {
    if (!articles.data?.length || !articleFromQuery) return;
    const found = articles.data.find((a) => a.id === articleFromQuery);
    if (found) setActive(found);
  }, [articles.data, articleFromQuery]);

  const clearArticleParam = useCallback(() => {
    if (!searchParams.has('article')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('article');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  // Keep open reader in sync with store score / edit mutations
  useEffect(() => {
    if (!active || !articles.data) return;
    const fresh = articles.data.find((a) => a.id === active.id);
    if (fresh && fresh !== active) {
      setActive(fresh);
    }
  }, [articles.data, active?.id]);

  const statusCounts = useMemo(() => {
    const list = articles.data ?? [];
    let pending = 0;
    let published = 0;
    for (const a of list) {
      if ((a.status ?? 'published') === 'pending') pending += 1;
      else published += 1;
    }
    return { all: list.length, pending, published };
  }, [articles.data]);

  const topicCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const a of articles.data ?? []) {
      map.set(a.topicId, (map.get(a.topicId) ?? 0) + 1);
    }
    return map;
  }, [articles.data]);

  const list = useMemo(() => {
    let items = [...(articles.data ?? [])];

    if (statusFilter !== 'all') {
      items = items.filter(
        (a) => (a.status ?? 'published') === statusFilter,
      );
    }

    if (topicId) {
      items = items.filter((a) => a.topicId === topicId);
    }

    if (query.trim()) {
      const q = query.toLowerCase();
      items = items.filter((a) => {
        const title = articleTitle(a, t).toLowerCase();
        const summary = articleSummary(a, t).toLowerCase();
        const tag = articleTag(a, t).toLowerCase();
        return title.includes(q) || summary.includes(q) || tag.includes(q);
      });
    }

    if (tab === 'recommended') {
      items = items
        .filter((a) => a.verified)
        .sort((a, b) => b.helpfulScore - a.helpfulScore);
      if (items.length === 0) {
        items = [...(articles.data ?? [])]
          .filter((a) => {
            if (statusFilter !== 'all' && (a.status ?? 'published') !== statusFilter) {
              return false;
            }
            if (topicId && a.topicId !== topicId) return false;
            if (!query.trim()) return true;
            const q = query.toLowerCase();
            return (
              articleTitle(a, t).toLowerCase().includes(q) ||
              articleSummary(a, t).toLowerCase().includes(q)
            );
          })
          .sort(
            (a, b) =>
              Number(b.verified) - Number(a.verified) ||
              b.helpfulScore - a.helpfulScore,
          );
      }
    } else if (tab === 'popular') {
      items = items.sort((a, b) => b.helpfulScore - a.helpfulScore);
    } else if (tab === 'updated') {
      items = items.sort(
        (a, b) =>
          new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
      );
    }

    return items;
  }, [articles.data, topicId, query, tab, t, statusFilter]);

  const sectionTitle =
    tab === 'popular'
      ? t('knowledge.forYouPopular')
      : tab === 'updated'
        ? t('knowledge.forYouUpdated')
        : t('knowledge.forYou');

  const sectionHint =
    tab === 'popular'
      ? t('knowledge.forYouPopularHint')
      : tab === 'updated'
        ? t('knowledge.forYouUpdatedHint')
        : t('knowledge.forYouHint');

  if (articles.error && !articles.loading && !articles.data) {
    return (
      <section className="page page--knowledge">
        <div className="knowledge-hero">
          <div>
            <p className="eyebrow">{t('knowledge.kicker')}</p>
            <h1>{t('knowledge.title')}</h1>
          </div>
        </div>
        <ErrorState onRetry={articles.reload} />
      </section>
    );
  }

  return (
    <section className="page page--knowledge">
      <div className="knowledge-hero">
        <div>
          <p className="eyebrow">{t('knowledge.kicker')}</p>
          <h1>{t('knowledge.title')}</h1>
          <p className="page-subtitle">{t('knowledge.subtitle')}</p>
        </div>
        <label className="knowledge-search">
          <Search size={19} aria-hidden />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('knowledge.searchPlaceholder')}
            aria-label={t('knowledge.searchPlaceholder')}
          />
        </label>
      </div>

      {!cmsWritable && (
        <div className="honesty-banner" role="status">
          <Shield size={16} aria-hidden />
          <p>{t('knowledge.cmsLiveBanner')}</p>
        </div>
      )}

      <Tabs
        value={tab}
        onChange={(id) => setTab(id as KnowledgeTab)}
        items={[
          { id: 'recommended', label: t('knowledge.tabRecommended') },
          { id: 'popular', label: t('knowledge.tabPopular') },
          { id: 'updated', label: t('knowledge.tabUpdated') },
        ]}
      />

      <div
        className="knowledge-status-filter"
        role="group"
        aria-label={t('knowledge.filterByStatus')}
      >
        {(
          [
            { id: 'all', label: t('knowledge.filterAll'), n: statusCounts.all },
            {
              id: 'published',
              label: t('knowledge.filterPublished'),
              n: statusCounts.published,
            },
            {
              id: 'pending',
              label: t('knowledge.filterPending'),
              n: statusCounts.pending,
            },
          ] as const
        ).map((f) => (
          <button
            key={f.id}
            type="button"
            className={statusFilter === f.id ? 'is-active' : undefined}
            aria-pressed={statusFilter === f.id}
            onClick={() => setStatusFilter(f.id)}
          >
            {f.label}
            <b>{f.n}</b>
          </button>
        ))}
      </div>

      <div className="knowledge-layout">
        <section>
          <div className="section-head">
            <div>
              <h2>{sectionTitle}</h2>
              <p>
                {topicId
                  ? t('knowledge.filteredByTopic', {
                      topic: t(
                        (topics.data ?? []).find((x) => x.id === topicId)
                          ?.titleKey ?? 'knowledge.topicsHeading',
                      ),
                    })
                  : sectionHint}
              </p>
            </div>
            {(query || topicId || statusFilter !== 'all') && (
              <button
                type="button"
                className="text-link"
                onClick={() => {
                  setQuery('');
                  setTopicId(null);
                  setStatusFilter('all');
                }}
              >
                {t('app.reset')}
              </button>
            )}
          </div>

          {articles.loading ? (
            <div className="article-stack">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} height={120} radius={11} />
              ))}
            </div>
          ) : list.length === 0 ? (
            <EmptyState
              title={t('knowledge.emptyTitle')}
              description={t('knowledge.emptyHint')}
              actionLabel={t('app.clearSearch')}
              onAction={() => {
                setQuery('');
                setTopicId(null);
                setStatusFilter('all');
              }}
            />
          ) : (
            <div className="article-stack">
              {list.map((a, index) => {
                const Icon = articleIcons[a.icon] ?? BookOpen;
                return (
                  <button
                    type="button"
                    className="article-card article-card--btn"
                    key={a.id}
                    onClick={() => setActive(a)}
                  >
                    <span
                      className={`article-illustration article-illustration--${index % 3}`}
                    >
                      <Icon size={25} />
                    </span>
                    <div className="article-body">
                      <span className="article-tag">{articleTag(a, t)}</span>
                      {a.status === 'pending' && (
                        <span className="article-pending-chip">
                          {t('knowledge.pending')}
                        </span>
                      )}
                      {a.status === 'published' && a.version != null && a.version > 1 && (
                        <span className="article-version-chip">
                          {t('knowledge.versionLabel', { n: a.version })}
                        </span>
                      )}
                      <h3>{articleTitle(a, t)}</h3>
                      <p>{articleSummary(a, t)}</p>
                      <small>
                        <Clock3 size={12} aria-hidden />
                        {t('knowledge.readMinutes', { n: a.readMinutes })}
                        <i />
                        {a.verified && (
                          <>
                            <CheckCircle2 size={12} aria-hidden />
                            {t('knowledge.verified')}
                          </>
                        )}
                      </small>
                    </div>
                    <div className="article-score">
                      <ThumbsUp size={14} aria-hidden />
                      <b>{a.helpfulScore}%</b>
                      <small>{t('knowledge.helpful')}</small>
                      <ArrowUpRight size={17} aria-hidden />
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <aside className="knowledge-rail">
          <div className="topic-card">
            <h3>{t('knowledge.topicsHeading')}</h3>
            <button
              type="button"
              className={!topicId ? 'is-active' : undefined}
              onClick={() => setTopicId(null)}
              aria-pressed={!topicId}
            >
              <span className="topic-dot topic-dot--all" />
              {t('knowledge.allTopics')}
              <b>{articles.data?.length ?? 0}</b>
              <ArrowRight size={14} aria-hidden />
            </button>
            {(topics.data ?? []).map((topic, i) => {
              const count = topicCounts.get(topic.id) ?? topic.count ?? 0;
              return (
                <button
                  type="button"
                  key={topic.id}
                  className={topicId === topic.id ? 'is-active' : undefined}
                  onClick={() =>
                    setTopicId((prev) =>
                      prev === topic.id ? null : topic.id,
                    )
                  }
                  aria-pressed={topicId === topic.id}
                >
                  <span className={`topic-dot topic-dot--${i % 4}`} />
                  {t(topic.titleKey)}
                  <b>{count}</b>
                  <ArrowRight size={14} aria-hidden />
                </button>
              );
            })}
          </div>
          {cmsWritable ? (
            <div className="contribute-card">
              <FileText size={19} />
              <h3>{t('knowledge.contributeTitle')}</h3>
              <p>{t('knowledge.contributeText')}</p>
              <button type="button" onClick={() => setContributeOpen(true)}>
                {t('knowledge.contributeAction')}
              </button>
            </div>
          ) : (
            <div className="contribute-card contribute-card--readonly">
              <FileText size={19} />
              <h3>{t('knowledge.contributeTitle')}</h3>
              <p>{t('knowledge.cmsLiveBanner')}</p>
            </div>
          )}
        </aside>
      </div>

      {active && (
        <ArticleReader
          article={active}
          all={articles.data ?? []}
          cmsWritable={cmsWritable}
          onClose={() => {
            setActive(null);
            clearArticleParam();
          }}
          onOpenRelated={(a) => setActive(a)}
          onArticleUpdated={(a) => {
            setActive(a);
            articles.reload();
          }}
        />
      )}

      {cmsWritable && (
        <ContributeModal
          open={contributeOpen}
          onClose={() => setContributeOpen(false)}
          onCreated={(a) => {
            setContributeOpen(false);
            success(t('knowledge.contributeSuccess', { title: a.title ?? '' }));
            articles.reload();
            setActive(a);
          }}
        />
      )}
    </section>
  );
}

function ArticleReader({
  article,
  all,
  cmsWritable,
  onClose,
  onOpenRelated,
  onArticleUpdated,
}: {
  article: KnowledgeArticle;
  all: KnowledgeArticle[];
  cmsWritable: boolean;
  onClose: () => void;
  onOpenRelated: (a: KnowledgeArticle) => void;
  onArticleUpdated: (a: KnowledgeArticle) => void;
}) {
  const t = useT();
  const { locale } = useI18n();
  const { success, error: toastError } = useToast();
  const ref = useRef<HTMLElement>(null);
  const [feedback, setFeedback] = useState<'yes' | 'no' | null>(
    () => readKnowledgeVote(article.id) ?? article.userVote ?? null,
  );
  const [scorePulse, setScorePulse] = useState(false);
  const [liveScore, setLiveScore] = useState(article.helpfulScore);
  const [liveYes, setLiveYes] = useState(article.helpfulYes ?? 0);
  const [liveNo, setLiveNo] = useState(article.helpfulNo ?? 0);
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editBody, setEditBody] = useState('');
  const [editTag, setEditTag] = useState('');
  const [editNote, setEditNote] = useState('');
  const [editErrors, setEditErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  useFocusTrap(ref, true);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (editing) {
          setEditing(false);
          return;
        }
        onClose();
      }
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose, editing]);

  useEffect(() => {
    setFeedback(readKnowledgeVote(article.id) ?? article.userVote ?? null);
    setLiveScore(article.helpfulScore);
    setLiveYes(article.helpfulYes ?? 0);
    setLiveNo(article.helpfulNo ?? 0);
    setScorePulse(false);
    setEditing(false);
  }, [article.id, article.helpfulScore, article.helpfulYes, article.helpfulNo, article.userVote]);

  const bodyText = articleBody(article, t);

  const startEdit = () => {
    setEditTitle(articleTitle(article, t));
    setEditBody(articleBody(article, t));
    setEditTag(articleTag(article, t));
    setEditNote(article.versionNote ?? '');
    setEditErrors({});
    setEditing(true);
  };

  const saveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!editTitle.trim()) next.title = t('knowledge.validation.title');
    if (!editBody.trim()) next.body = t('knowledge.validation.body');
    setEditErrors(next);
    if (Object.keys(next).length) return;
    setSaving(true);
    try {
      const updated = await updateKnowledgeArticle(article.id, {
        title: editTitle.trim(),
        body: editBody.trim(),
        tag: editTag.trim(),
        versionNote: editNote.trim() || t('knowledge.defaultVersionNote'),
      });
      if (!updated) {
        toastError(t('knowledge.editFailed'));
        return;
      }
      setEditing(false);
      onArticleUpdated(updated);
      success(t('knowledge.editSuccess'));
    } catch (err) {
      toastError(
        isLiveFeatureUnsupported(err)
          ? t('knowledge.cmsLiveUnsupported')
          : t('knowledge.editFailed'),
      );
    } finally {
      setSaving(false);
    }
  };

  const onPublish = async () => {
    setPublishing(true);
    try {
      const updated = await publishKnowledgeArticle(article.id);
      if (!updated) {
        toastError(t('knowledge.publishFailed'));
        return;
      }
      onArticleUpdated(updated);
      success(t('knowledge.publishSuccess', { title: articleTitle(updated, t) }));
    } catch (err) {
      toastError(
        isLiveFeatureUnsupported(err)
          ? t('knowledge.cmsLiveUnsupported')
          : t('knowledge.publishFailed'),
      );
    } finally {
      setPublishing(false);
    }
  };

  /** S10 / S36: open incident pre-filled from this article. */
  const [usingInTicket, setUsingInTicket] = useState(false);
  const useInTicket = async () => {
    setUsingInTicket(true);
    try {
      const title = articleTitle(article, t);
      const summary = articleSummary(article, t);
      const created = await createWorkItem({
        kind: 'incident',
        title: t('knowledge.useInTicketTitle', { title }),
        description: t('knowledge.useInTicketBody', {
          title,
          summary,
          articleId: article.id,
        }),
        service: t('knowledge.useInTicketService'),
        priority: 'medium',
      });
      success(t('knowledge.useInTicketSuccess', { number: created.number }), {
        label: t('knowledge.useInTicketOpen'),
        href: `/work-items/${created.id}`,
      });
    } catch {
      toastError(t('knowledge.useInTicketFailed'));
    } finally {
      setUsingInTicket(false);
    }
  };

  const related = useMemo(() => {
    return all
      .filter((a) => a.id !== article.id)
      .sort((a, b) => {
        const sameTopic =
          Number(b.topicId === article.topicId) -
          Number(a.topicId === article.topicId);
        if (sameTopic !== 0) return sameTopic;
        return b.helpfulScore - a.helpfulScore;
      })
      .slice(0, 3);
  }, [all, article]);

  const onFeedback = async (value: 'yes' | 'no') => {
    if (feedback) return;
    const updated = await submitKnowledgeVote(article.id, value);
    if (!updated) {
      setFeedback(value);
      return;
    }
    setFeedback(value);
    setLiveScore(updated.helpfulScore);
    setLiveYes(updated.helpfulYes ?? 0);
    setLiveNo(updated.helpfulNo ?? 0);
    setScorePulse(true);
    onArticleUpdated(updated);
    success(
      value === 'yes'
        ? t('knowledge.feedbackThanks')
        : t('knowledge.feedbackThanksNo'),
    );
  };

  const onPrint = () => {
    window.print();
  };

  const voteTotal = liveYes + liveNo;

  return (
    <div
      className="modal-backdrop article-reader-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !editing) onClose();
      }}
    >
      <article
        ref={ref}
        className="modal modal--lg article-reader"
        role="dialog"
        aria-modal="true"
        aria-labelledby="kb-reader-title"
      >
        <div className="article-reader__head">
          <div>
            <span className="article-tag">{articleTag(article, t)}</span>
            {article.status === 'pending' && (
              <span className="article-pending-chip">
                {t('knowledge.pending')}
              </span>
            )}
            {article.status === 'published' && (
              <span className="article-published-chip">
                {t('knowledge.published')}
              </span>
            )}
            {article.version != null && article.version > 0 && (
              <span className="article-version-chip">
                {t('knowledge.versionLabel', { n: article.version })}
              </span>
            )}
            <h2 id="kb-reader-title">{articleTitle(article, t)}</h2>
            <div className="article-reader__meta">
              <span>
                <Clock3 size={13} aria-hidden />
                {t('knowledge.readMinutes', { n: article.readMinutes })}
              </span>
              {article.verified && (
                <span>
                  <CheckCircle2 size={13} aria-hidden />
                  {t('knowledge.verified')}
                </span>
              )}
              <span>
                {t('workItem.updated')}:{' '}
                {formatDateTime(article.updatedAt, locale)}
              </span>
            </div>
            {article.versionNote?.trim() && (
              <p className="article-version-note">
                <strong>{t('knowledge.versionNoteLabel')}:</strong>{' '}
                {article.versionNote}
              </p>
            )}
          </div>
          <div className="article-reader__tools">
            {cmsWritable && !editing && (
              <button
                type="button"
                className="icon-btn"
                aria-label={t('knowledge.editArticle')}
                onClick={startEdit}
                title={t('knowledge.editArticle')}
              >
                <Pencil size={18} />
              </button>
            )}
            <button
              type="button"
              className="icon-btn"
              aria-label={t('knowledge.printArticle')}
              onClick={onPrint}
              title={t('knowledge.printArticle')}
            >
              <Printer size={18} />
            </button>
            <button
              type="button"
              className="icon-btn"
              aria-label={t('app.close')}
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {editing ? (
          <form
            className="article-reader__body module-create-form module-create-form--contribute"
            onSubmit={(e) => void saveEdit(e)}
          >
            <p className="muted" style={{ margin: 0, fontSize: 'var(--text-sm)' }}>
              {t('knowledge.editHint')}
            </p>
            <Input
              label={t('knowledge.contributeFieldTitle')}
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              error={editErrors.title}
              required
              autoFocus
            />
            <Input
              label={t('knowledge.editFieldTag')}
              value={editTag}
              onChange={(e) => setEditTag(e.target.value)}
              hint={t('knowledge.editFieldTagHint')}
            />
            <Textarea
              label={t('knowledge.contributeFieldBody')}
              value={editBody}
              onChange={(e) => setEditBody(e.target.value)}
              error={editErrors.body}
              rows={8}
              required
            />
            <Input
              label={t('knowledge.versionNoteField')}
              value={editNote}
              onChange={(e) => setEditNote(e.target.value)}
              hint={t('knowledge.versionNoteHint')}
              placeholder={t('knowledge.versionNotePlaceholder')}
            />
            <div className="module-create-form__actions">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setEditing(false)}
              >
                {t('app.cancel')}
              </Button>
              <Button type="submit" variant="primary" disabled={saving}>
                {saving ? t('app.loading') : t('knowledge.saveEdit')}
              </Button>
            </div>
          </form>
        ) : (
          <>
            <div className="article-reader__body">
              {bodyText.split('\n').map((para, i) =>
                para.trim() ? <p key={i}>{para}</p> : <br key={i} />,
              )}

              {related.length > 0 && (
                <div className="article-related">
                  <h3>{t('knowledge.relatedArticles')}</h3>
                  <ul>
                    {related.map((r) => (
                      <li key={r.id}>
                        <button type="button" onClick={() => onOpenRelated(r)}>
                          <span className="article-tag">{articleTag(r, t)}</span>
                          <span className="article-related__title">
                            {articleTitle(r, t)}
                          </span>
                          <small>
                            {t('knowledge.readMinutes', { n: r.readMinutes })}
                          </small>
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
            <div className="article-reader__foot">
              <div className="article-feedback">
                <span className="article-feedback__label">
                  {t('knowledge.wasHelpful')}
                </span>
                <button
                  type="button"
                  className={`article-feedback__btn${feedback === 'yes' ? ' is-active' : ''}`}
                  onClick={() => void onFeedback('yes')}
                  disabled={feedback !== null}
                  aria-pressed={feedback === 'yes'}
                >
                  <ThumbsUp size={15} aria-hidden />
                  {t('knowledge.feedbackYes')}
                </button>
                <button
                  type="button"
                  className={`article-feedback__btn${feedback === 'no' ? ' is-active' : ''}`}
                  onClick={() => void onFeedback('no')}
                  disabled={feedback !== null}
                  aria-pressed={feedback === 'no'}
                >
                  <ThumbsDown size={15} aria-hidden />
                  {t('knowledge.feedbackNo')}
                </button>
                <span
                  className={`article-feedback__score-live${scorePulse ? ' is-updated' : ''}`}
                  aria-live="polite"
                >
                  <ThumbsUp size={14} aria-hidden />
                  <b>{liveScore}%</b>
                  <small>{t('knowledge.helpful')}</small>
                  {voteTotal > 0 && (
                    <span className="article-feedback__votes">
                      {t('knowledge.voteCount', { n: voteTotal })}
                    </span>
                  )}
                </span>
              </div>
              <div className="article-reader__foot-actions">
                {cmsWritable && article.status === 'pending' && (
                  <Button
                    variant="primary"
                    onClick={() => void onPublish()}
                    disabled={publishing}
                  >
                    {publishing ? t('app.loading') : t('knowledge.publish')}
                  </Button>
                )}
                <Button
                  variant="secondary"
                  onClick={() => void useInTicket()}
                  disabled={usingInTicket}
                  title={t('knowledge.useInTicketHint')}
                >
                  {usingInTicket ? t('app.loading') : t('knowledge.useInTicket')}
                </Button>
                <Button variant="secondary" onClick={onClose}>
                  {t('app.close')}
                </Button>
              </div>
            </div>
          </>
        )}
      </article>
    </div>
  );
}

function ContributeModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (a: KnowledgeArticle) => void;
}) {
  const t = useT();
  const { error: toastError } = useToast();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [tag, setTag] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setTitle('');
      setBody('');
      setTag('');
      setErrors({});
      setSubmitting(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = t('knowledge.validation.title');
    if (!body.trim()) next.body = t('knowledge.validation.body');
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      const created = await createKnowledgeArticle({
        title: title.trim(),
        body: body.trim(),
        tag: tag.trim() || undefined,
        status: 'pending',
      });
      onCreated(created);
    } catch (err) {
      toastError(
        isLiveFeatureUnsupported(err)
          ? t('knowledge.cmsLiveUnsupported')
          : t('knowledge.editFailed'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('knowledge.contributeModalTitle')}
      labelledBy="contribute-kb-title"
      size="lg"
    >
      <form
        className="module-create-form module-create-form--contribute"
        onSubmit={(e) => void submit(e)}
      >
        <p className="muted" style={{ margin: 0, fontSize: 'var(--text-sm)' }}>
          {t('knowledge.contributeModalHint')}
        </p>
        <Input
          label={t('knowledge.contributeFieldTitle')}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={errors.title}
          required
          autoFocus
        />
        <Input
          label={t('knowledge.editFieldTag')}
          value={tag}
          onChange={(e) => setTag(e.target.value)}
          hint={t('knowledge.editFieldTagHint')}
        />
        <Textarea
          label={t('knowledge.contributeFieldBody')}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          error={errors.body}
          rows={5}
          required
          hint={t('knowledge.contributeFieldBodyHint')}
        />
        <div className="module-create-form__actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {t('knowledge.contributeSubmit')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
