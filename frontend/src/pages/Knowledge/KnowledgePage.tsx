import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  CheckCircle2,
  Clock3,
  FileText,
  KeyRound,
  Laptop,
  Printer,
  Search,
  Shield,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import { fetchKnowledgeArticles, fetchKnowledgeTopics } from '@/api';
import { Button, EmptyState, ErrorState, Skeleton, Tabs } from '@/components/ui';
import type { KnowledgeArticle } from '@/types';
import { formatDateTime } from '@/lib/format';

const articleIcons = {
  key: KeyRound,
  shield: Shield,
  laptop: Laptop,
  book: BookOpen,
} as const;

type KnowledgeTab = 'recommended' | 'popular' | 'updated';

export function KnowledgePage() {
  const t = useT();
  const { success } = useToast();
  const [tab, setTab] = useState<KnowledgeTab>('recommended');
  const [query, setQuery] = useState('');
  const [topicId, setTopicId] = useState<string | null>(null);
  const [active, setActive] = useState<KnowledgeArticle | null>(null);
  const articles = useAsync(() => fetchKnowledgeArticles(), []);
  const topics = useAsync(() => fetchKnowledgeTopics(), []);

  const topicCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const a of articles.data ?? []) {
      map.set(a.topicId, (map.get(a.topicId) ?? 0) + 1);
    }
    return map;
  }, [articles.data]);

  const list = useMemo(() => {
    let items = [...(articles.data ?? [])];

    if (topicId) {
      items = items.filter((a) => a.topicId === topicId);
    }

    if (query.trim()) {
      const q = query.toLowerCase();
      items = items.filter(
        (a) =>
          t(a.titleKey).toLowerCase().includes(q) ||
          t(a.summaryKey).toLowerCase().includes(q) ||
          t(a.tagKey).toLowerCase().includes(q),
      );
    }

    if (tab === 'recommended') {
      items = items
        .filter((a) => a.verified)
        .sort((a, b) => b.helpfulScore - a.helpfulScore);
      // If filter wiped everything, fall back to full scored list
      if (items.length === 0) {
        items = [...(articles.data ?? [])]
          .filter((a) => {
            if (topicId && a.topicId !== topicId) return false;
            if (!query.trim()) return true;
            const q = query.toLowerCase();
            return (
              t(a.titleKey).toLowerCase().includes(q) ||
              t(a.summaryKey).toLowerCase().includes(q)
            );
          })
          .sort((a, b) => Number(b.verified) - Number(a.verified) || b.helpfulScore - a.helpfulScore);
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
  }, [articles.data, topicId, query, tab, t]);

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

      <Tabs
        value={tab}
        onChange={(id) => setTab(id as KnowledgeTab)}
        items={[
          { id: 'recommended', label: t('knowledge.tabRecommended') },
          { id: 'popular', label: t('knowledge.tabPopular') },
          { id: 'updated', label: t('knowledge.tabUpdated') },
        ]}
      />

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
            {(query || topicId) && (
              <button
                type="button"
                className="text-link"
                onClick={() => {
                  setQuery('');
                  setTopicId(null);
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
                      <span className="article-tag">{t(a.tagKey)}</span>
                      <h3>{t(a.titleKey)}</h3>
                      <p>{t(a.summaryKey)}</p>
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
              const count =
                topicCounts.get(topic.id) ?? topic.count ?? 0;
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
          <div className="contribute-card">
            <FileText size={19} />
            <h3>{t('knowledge.contributeTitle')}</h3>
            <p>{t('knowledge.contributeText')}</p>
            <button
              type="button"
              onClick={() => success(t('knowledge.contributeMock'))}
            >
              {t('knowledge.contributeAction')}
            </button>
          </div>
        </aside>
      </div>

      {active && (
        <ArticleReader
          article={active}
          all={articles.data ?? []}
          onClose={() => setActive(null)}
          onOpenRelated={(a) => setActive(a)}
        />
      )}
    </section>
  );
}

function ArticleReader({
  article,
  all,
  onClose,
  onOpenRelated,
}: {
  article: KnowledgeArticle;
  all: KnowledgeArticle[];
  onClose: () => void;
  onOpenRelated: (a: KnowledgeArticle) => void;
}) {
  const t = useT();
  const { locale } = useI18n();
  const { success } = useToast();
  const ref = useRef<HTMLElement>(null);
  const [feedback, setFeedback] = useState<'yes' | 'no' | null>(null);
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

  // Reset feedback when article changes
  useEffect(() => {
    setFeedback(null);
  }, [article.id]);

  const bodyKey = article.titleKey.replace('.title', '.body');
  const body = t(bodyKey);
  const bodyText =
    body === bodyKey
      ? `${t(article.summaryKey)}\n\n${t('knowledge.articleBodyFallback')}`
      : body;

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

  const onFeedback = (value: 'yes' | 'no') => {
    setFeedback(value);
    success(
      value === 'yes'
        ? t('knowledge.feedbackThanks')
        : t('knowledge.feedbackThanksNo'),
    );
  };

  const onPrint = () => {
    window.print();
  };

  return (
    <div
      className="modal-backdrop article-reader-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
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
            <span className="article-tag">{t(article.tagKey)}</span>
            <h2 id="kb-reader-title">{t(article.titleKey)}</h2>
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
          </div>
          <div className="article-reader__tools">
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
                    <button
                      type="button"
                      onClick={() => onOpenRelated(r)}
                    >
                      <span className="article-tag">{t(r.tagKey)}</span>
                      <span className="article-related__title">
                        {t(r.titleKey)}
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
              onClick={() => onFeedback('yes')}
              disabled={feedback !== null}
              aria-pressed={feedback === 'yes'}
            >
              <ThumbsUp size={15} aria-hidden />
              {t('knowledge.feedbackYes')}
            </button>
            <button
              type="button"
              className={`article-feedback__btn${feedback === 'no' ? ' is-active' : ''}`}
              onClick={() => onFeedback('no')}
              disabled={feedback !== null}
              aria-pressed={feedback === 'no'}
            >
              <ThumbsDown size={15} aria-hidden />
              {t('knowledge.feedbackNo')}
            </button>
            <span className="article-score article-score--inline">
              <ThumbsUp size={14} aria-hidden />
              <b>{article.helpfulScore}%</b>
              <small>{t('knowledge.helpful')}</small>
            </span>
          </div>
          <Button variant="secondary" onClick={onClose}>
            {t('app.close')}
          </Button>
        </div>
      </article>
    </div>
  );
}
