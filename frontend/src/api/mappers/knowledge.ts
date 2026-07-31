import type { KnowledgeArticle, KnowledgeTopic } from '@/types';

export interface BackendArticleSummary {
  id: string;
  number?: string;
  slug?: string;
  status?: string;
  version?: number;
  ownerSubject?: string;
  nextReviewAt?: string | null;
  title: string;
  summary?: string | null;
  locale?: string;
}

const ICONS: KnowledgeArticle['icon'][] = ['key', 'shield', 'laptop', 'book'];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function mapKnowledgeArticle(dto: BackendArticleSummary): KnowledgeArticle {
  const id = String(dto.id);
  const statusUpper = (dto.status ?? '').toUpperCase();
  const published = statusUpper === 'PUBLISHED';
  return {
    id,
    titleKey: dto.title,
    summaryKey: dto.summary ?? dto.title,
    tagKey: dto.slug ?? dto.number ?? id,
    title: dto.title,
    summary: dto.summary ?? dto.title,
    readMinutes: 5,
    helpfulScore: 0,
    verified: published,
    icon: ICONS[hash(id) % ICONS.length],
    topicId: 'general',
    updatedAt: dto.nextReviewAt ?? new Date().toISOString(),
    status: published ? 'published' : 'pending',
    version: dto.version,
  };
}

export function deriveKnowledgeTopics(
  articles: KnowledgeArticle[],
): KnowledgeTopic[] {
  const counts = new Map<string, number>();
  for (const a of articles) {
    counts.set(a.topicId, (counts.get(a.topicId) ?? 0) + 1);
  }
  if (counts.size === 0) {
    return [{ id: 'general', titleKey: 'General', count: 0 }];
  }
  return [...counts.entries()].map(([id, count]) => ({
    id,
    titleKey: id,
    count,
  }));
}
