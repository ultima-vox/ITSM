import { delay, isMockMode, apiRequest } from './client';
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

interface BackendArticleDetail extends BackendArticleSummary {
  body?: string | null;
  authorSubject?: string | null;
  revisionCreatedAt?: string | null;
}

function mapDetail(dto: BackendArticleDetail): KnowledgeArticle {
  const base = mapKnowledgeArticle(dto);
  return {
    ...base,
    body: dto.body ?? base.body,
    summary: dto.summary ?? base.summary,
  };
}

export async function fetchKnowledgeArticles(): Promise<KnowledgeArticle[]> {
  if (isMockMode()) {
    await delay(220);
    return listKnowledgeArticles();
  }
  // CMS list includes drafts when caller has knowledge.write
  try {
    const list = await apiRequest<BackendArticleSummary[]>(
      '/knowledge/articles?publishedOnly=false',
    );
    return (list ?? []).map(mapKnowledgeArticle);
  } catch {
    const list = await apiRequest<BackendArticleSummary[]>('/knowledge/articles');
    return (list ?? []).map(mapKnowledgeArticle);
  }
}

export async function fetchKnowledgeTopics(): Promise<KnowledgeTopic[]> {
  if (isMockMode()) {
    await delay(180);
    return knowledgeTopics;
  }
  const articles = await fetchKnowledgeArticles();
  return deriveKnowledgeTopics(articles);
}

export async function submitKnowledgeVote(
  id: string,
  vote: 'yes' | 'no',
): Promise<KnowledgeArticle | null> {
  if (isMockMode()) {
    await delay(120);
    return voteKnowledgeArticle(id, vote);
  }
  try {
    await apiRequest(`/knowledge/articles/${id}/votes`, {
      method: 'POST',
      body: { helpful: vote === 'yes' },
    });
    return null;
  } catch {
    return null;
  }
}

export function readKnowledgeVote(id: string): 'yes' | 'no' | null {
  if (!isMockMode()) return null;
  return getKnowledgeVote(id);
}

export async function createKnowledgeArticle(
  payload: CreateKnowledgeArticlePayload,
): Promise<KnowledgeArticle> {
  if (isMockMode()) {
    await delay(180);
    return storeAddArticle(payload);
  }
  const created = await apiRequest<BackendArticleDetail>('/knowledge/articles', {
    method: 'POST',
    body: {
      title: payload.title,
      body: payload.body,
      summary: payload.title,
      locale: 'ru',
    },
  });
  return mapDetail(created);
}

export async function updateKnowledgeArticle(
  id: string,
  payload: UpdateKnowledgeArticlePayload,
): Promise<KnowledgeArticle | null> {
  if (isMockMode()) {
    await delay(140);
    return storeUpdateArticle(id, payload);
  }
  const updated = await apiRequest<BackendArticleDetail>(
    `/knowledge/articles/${encodeURIComponent(id)}`,
    {
      method: 'PUT',
      body: {
        title: payload.title,
        body: payload.body,
        versionNote: payload.versionNote,
        locale: 'ru',
      },
    },
  );
  return mapDetail(updated);
}

export async function publishKnowledgeArticle(
  id: string,
): Promise<KnowledgeArticle | null> {
  if (isMockMode()) {
    await delay(140);
    return storePublishArticle(id);
  }
  const published = await apiRequest<BackendArticleDetail>(
    `/knowledge/articles/${encodeURIComponent(id)}/publish`,
    { method: 'POST' },
  );
  return mapDetail(published);
}

export { subscribeKnowledge };
