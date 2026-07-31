import { useEffect, useRef, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  Clock3,
  FileText,
  Filter,
  KeyRound,
  Laptop,
  Search,
  Shield,
  ThumbsUp,
  X,
} from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { fetchKnowledgeArticles, fetchKnowledgeTopics } from '@/api';
import { Button, EmptyState, ErrorState, Skeleton, Tabs } from '@/components/ui';
import type { KnowledgeArticle } from '@/types';
import { formatDateTime } from '@/lib/format';

const articleIcons = [KeyRound, Shield, Laptop];

export function KnowledgePage() {
  const t = useT();
  const [tab, setTab] = useState('recommended');
  const [query, setQuery] = useState('');
  const [active, setActive] = useState<KnowledgeArticle | null>(null);
  const articles = useAsync(() => fetchKnowledgeArticles(), []);
  const topics = useAsync(() => fetchKnowledgeTopics(), []);

  const list = (articles.data ?? []).filter((a) => {
    if (!query.trim()) return true;
    const q = query.toLowerCase();
    return (
      t(a.titleKey).toLowerCase().includes(q) ||
      t(a.summaryKey).toLowerCase().includes(q)
    );
  });

  if (
    articles.error &&
    !articles.loading &&
    !articles.data
  ) {
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
        onChange={setTab}
        items={[
          { id: 'recommended', label: t('knowledge.tabRecommended') },
          { id: 'popular', label: t('knowledge.tabPopular') },
          { id: 'updated', label: t('knowledge.tabUpdated') },
        ]}
        trailing={
          <button type="button" className="tabs__filter">
            <Filter size={15} />
            {t('app.filters')}
          </button>
        }
      />

      <div className="knowledge-layout">
        <section>
          <div className="section-head">
            <div>
              <h2>{t('knowledge.forYou')}</h2>
              <p>{t('knowledge.forYouHint')}</p>
            </div>
            <button type="button" className="text-link">
              {t('knowledge.viewAll')} <ArrowRight size={15} />
            </button>
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
              onAction={() => setQuery('')}
            />
          ) : (
            <div className="article-stack">
              {list.map((a, index) => {
                const Icon = articleIcons[index % articleIcons.length];
                return (
                  <button
                    type="button"
                    className="article-card article-card--btn"
                    key={a.id}
                    onClick={() => setActive(a)}
                  >
                    <span
                      className={`article-illustration article-illustration--${index}`}
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
            {(topics.data ?? []).map((topic, i) => (
              <button type="button" key={topic.id}>
                <span className={`topic-dot topic-dot--${i}`} />
                {t(topic.titleKey)}
                <b>{topic.count}</b>
                <ArrowRight size={14} aria-hidden />
              </button>
            ))}
          </div>
          <div className="contribute-card">
            <FileText size={19} />
            <h3>{t('knowledge.contributeTitle')}</h3>
            <p>{t('knowledge.contributeText')}</p>
            <button type="button">{t('knowledge.contributeAction')}</button>
          </div>
        </aside>
      </div>

      {active && (
        <ArticleReader article={active} onClose={() => setActive(null)} />
      )}
    </section>
  );
}

function ArticleReader({
  article,
  onClose,
}: {
  article: KnowledgeArticle;
  onClose: () => void;
}) {
  const t = useT();
  const { locale } = useI18n();
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

  const bodyKey = article.titleKey.replace('.title', '.body');
  const body = t(bodyKey);
  const bodyText =
    body === bodyKey
      ? `${t(article.summaryKey)}\n\n${t('knowledge.articleBodyFallback')}`
      : body;

  return (
    <div
      className="modal-backdrop"
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
          <button
            type="button"
            className="icon-btn"
            aria-label={t('app.close')}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        <div className="article-reader__body">
          {bodyText.split('\n').map((para, i) =>
            para.trim() ? <p key={i}>{para}</p> : <br key={i} />,
          )}
        </div>
        <div className="article-reader__foot">
          <span className="article-score article-score--inline">
            <ThumbsUp size={14} aria-hidden />
            <b>{article.helpfulScore}%</b>
            <small>{t('knowledge.helpful')}</small>
          </span>
          <Button variant="secondary" onClick={onClose}>
            {t('app.close')}
          </Button>
        </div>
      </article>
    </div>
  );
}
