import { delay, useMock, apiRequest, refuseLiveFeature } from './client';
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
  publishKnowledgeArticle as storePublishArticle,
  subscribeKnowledge,
  updateKnowledgeArticle as storeUpdateArticle,
  voteKnowledgeArticle,
} from '@/mock/store';
import type {
  CreateKnowledgeArticlePayload,
  KnowledgeArticle,
  KnowledgeTopic,
  UpdateKnowledgeArticlePayload,
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
  try {
    await apiRequest(`/knowledge/articles/${id}/votes`, {
      method: 'POST',
      body: { helpful: vote === 'yes' },
    });
    // Backend returns vote receipt, not article — re-list is too heavy; no-op UI keeps prior score
    return null;
  } catch {
    return null;
  }
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
  // S25: no live write API — refuse split-brain ghost articles
  refuseLiveFeature('knowledge.cmsLiveUnsupported');
}

export async function updateKnowledgeArticle(
  id: string,
  payload: UpdateKnowledgeArticlePayload,
): Promise<KnowledgeArticle | null> {
  if (useMock()) {
    await delay(140);
    return storeUpdateArticle(id, payload);
  }
  refuseLiveFeature('knowledge.cmsLiveUnsupported');
}

export async function publishKnowledgeArticle(
  id: string,
): Promise<KnowledgeArticle | null> {
  if (useMock()) {
    await delay(140);
    return storePublishArticle(id);
  }
  refuseLiveFeature('knowledge.cmsLiveUnsupported');
}

export { subscribeKnowledge };
