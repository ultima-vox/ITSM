import { delay, isMockMode, apiRequest } from './client';
import {
  deriveCatalogCategories,
  mapCatalogService,
  type BackendCatalogItem,
} from './mappers/catalog';
import { catalogCategories, catalogServices } from '@/mock/data';
import type { CatalogCategory, CatalogService } from '@/types';

export async function fetchCatalogCategories(): Promise<CatalogCategory[]> {
  if (isMockMode()) {
    await delay(200);
    return catalogCategories;
  }
  // Backend has GET /catalog/items only — derive categories from items
  const items = await apiRequest<BackendCatalogItem[]>('/catalog/items');
  const services = (items ?? []).map(mapCatalogService);
  return deriveCatalogCategories(services);
}

export async function fetchCatalogServices(q?: string): Promise<CatalogService[]> {
  if (isMockMode()) {
    await delay(220);
    if (!q) return catalogServices;
    const needle = q.toLowerCase();
    return catalogServices.filter(
      (s) =>
        s.titleKey.toLowerCase().includes(needle) ||
        s.id.toLowerCase().includes(needle),
    );
  }
  const qs = new URLSearchParams();
  if (q) qs.set('q', q);
  const suffix = qs.toString() ? `?${qs}` : '';
  const items = await apiRequest<BackendCatalogItem[]>(`/catalog/items${suffix}`);
  return (items ?? []).map(mapCatalogService);
}

export interface SubmitCatalogRequestPayload {
  formPayload?: Record<string, unknown>;
}

export interface SubmittedCatalogRequest {
  id: string;
  workItemId?: string;
  number?: string;
}

export interface CatalogRequestView {
  id: string;
  number: string;
  catalogItemId: string;
  catalogItemKey: string;
  status: string;
  formPayload: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogApprovalView {
  id: string; approverRole: string; state: string; decidedBy?: string | null;
  decidedAt?: string | null; comment?: string | null; createdAt: string;
}

export interface CatalogTaskView {
  id: string; title: string; state: string; assigneeId?: string | null;
  createdAt: string; completedAt?: string | null;
}

export async function fetchMyCatalogRequests(): Promise<CatalogRequestView[]> {
  if (isMockMode()) return [];
  return apiRequest<CatalogRequestView[]>('/catalog/requests?page=0&size=100');
}

export async function fetchCatalogRequestApprovals(id: string): Promise<CatalogApprovalView[]> {
  if (isMockMode()) return [];
  return apiRequest<CatalogApprovalView[]>(`/catalog/requests/${id}/approvals`);
}

export async function fetchCatalogRequestTasks(id: string): Promise<CatalogTaskView[]> {
  if (isMockMode()) return [];
  return apiRequest<CatalogTaskView[]>(`/catalog/requests/${id}/tasks`);
}

export async function fetchCatalogOperations(): Promise<CatalogRequestView[]> {
  if (isMockMode()) return [];
  return apiRequest<CatalogRequestView[]>('/catalog/operations/requests?page=0&size=100');
}

export async function decideCatalogApproval(
  requestId: string, approvalId: string, decision: 'APPROVED' | 'REJECTED', comment?: string,
): Promise<CatalogApprovalView> {
  return apiRequest<CatalogApprovalView>(
    `/catalog/requests/${requestId}/approvals/${approvalId}/decision`,
    { method: 'POST', body: { decision, comment: comment?.trim() || null } },
  );
}

export async function updateCatalogTask(
  requestId: string, taskId: string, state: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED', assigneeId?: string,
): Promise<CatalogTaskView> {
  return apiRequest<CatalogTaskView>(`/catalog/requests/${requestId}/tasks/${taskId}`, {
    method: 'POST', body: { state, assigneeId: assigneeId?.trim() || null },
  });
}

/**
 * Live catalog request: POST /catalog/items/{id}/requests.
 * Mock mode is not used — callers should prefer createWorkItem for mock.
 */
export async function submitCatalogRequest(
  itemId: string,
  payload: SubmitCatalogRequestPayload = {},
): Promise<SubmittedCatalogRequest> {
  if (isMockMode()) {
    throw new Error('submitCatalogRequest is live-only; use createWorkItem in mock mode');
  }
  const result = await apiRequest<{
    id: string;
    workItemId?: string;
    number?: string;
  }>(`/catalog/items/${itemId}/requests`, {
    method: 'POST',
    body: { formPayload: payload.formPayload ?? {} },
  });
  return {
    id: String(result.id),
    workItemId: result.workItemId ? String(result.workItemId) : undefined,
    number: result.number,
  };
}
