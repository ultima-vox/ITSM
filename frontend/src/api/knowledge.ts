import { delay, useMock, apiRequest } from './client';
import {
  deriveKnowledgeTopics,
  mapKnowledgeArticle,
  type BackendArticleSummary,
} from './mappers/knowledge';
import { knowledgeTopics } from '@/mock/data';
import {
  addKnowledgeArticle as storeAddArticle,
  getKnowledgeVote,
  listKnowledgeArticles,
  subscribeKnowledge,
  voteKnowledgeArticle,
} from '@/mock/store';
import type {
  CreateKnowledgeArticlePayload,
  KnowledgeArticle,
  KnowledgeTopic,
} from '@/types';

export async function fetchKnowledgeArticles(): Promise<KnowledgeArticle[]> {
  if (useMock()) {
    await delay(220);
    return listKnowledgeArticles();
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

export async function submitKnowledgeVote(
  id: string,
  vote: 'yes' | 'no',
): Promise<KnowledgeArticle | null> {
  if (useMock()) {
    await delay(120);
    return voteKnowledgeArticle(id, vote);
  }
  // Live backend: no vote endpoint yet — no-op keep UI responsive
  return null;
}

export function readKnowledgeVote(id: string): 'yes' | 'no' | null {
  if (!useMock()) return null;
  return getKnowledgeVote(id);
}

export async function createKnowledgeArticle(
  payload: CreateKnowledgeArticlePayload,
): Promise<KnowledgeArticle> {
  if (useMock()) {
    await delay(180);
    return storeAddArticle(payload);
  }
  // Backend authoring not wired — mock-first contribute path
  return storeAddArticle(payload);
}

export { subscribeKnowledge };
