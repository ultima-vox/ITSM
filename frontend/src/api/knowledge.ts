import { delay, useMock, apiRequest } from './client';
import {
  deriveKnowledgeTopics,
  mapKnowledgeArticle,
  type BackendArticleSummary,
} from './mappers/knowledge';
import { knowledgeArticles, knowledgeTopics } from '@/mock/data';
import type { KnowledgeArticle, KnowledgeTopic } from '@/types';

export async function fetchKnowledgeArticles(): Promise<KnowledgeArticle[]> {
  if (useMock()) {
    await delay(220);
    return knowledgeArticles;
  }
  const list = await apiRequest<BackendArticleSummary[]>('/knowledge/articles');
  return (list ?? []).map(mapKnowledgeArticle);
}

export async function fetchKnowledgeTopics(): Promise<KnowledgeTopic[]> {
  if (useMock()) {
    await delay(180);
    return knowledgeTopics;
  }
  // Backend has no topics resource — derive from articles
  const list = await apiRequest<BackendArticleSummary[]>('/knowledge/articles');
  const articles = (list ?? []).map(mapKnowledgeArticle);
  return deriveKnowledgeTopics(articles);
}
