/**
 * Attachments API — multipart upload + work-item link persistence.
 * Mock mode: in-memory meta + work-item link map.
 */

import { ApiError, apiFetch, apiRequest, delay, getBaseUrl, isMockMode } from './client';

export type AttachmentScanStatus =
  | 'PENDING'
  | 'CLEAN'
  | 'INFECTED'
  | 'SKIPPED'
  | 'ERROR';

export interface AttachmentMeta {
  id: string;
  filename: string;
  contentType: string;
  size: number;
  objectKey: string;
  linkedBy?: string;
  linkedAt?: string;
  scanStatus?: AttachmentScanStatus;
  scanEngine?: string;
  scanDetail?: string;
}

/** In-memory mock store: id → meta */
const mockStore = new Map<string, AttachmentMeta>();
/** workItemId → attachment ids */
const mockLinks = new Map<string, string[]>();

function mockId(): string {
  return `att-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/**
 * Upload a file via multipart/form-data field `file`.
 * Do NOT set Content-Type to application/json — browser sets multipart boundary.
 */
export async function uploadAttachment(file: File): Promise<AttachmentMeta> {
  if (isMockMode()) {
    await delay(320);
    const infected = file.name.toLowerCase().includes('eicar');
    const meta: AttachmentMeta = {
      id: mockId(),
      filename: file.name,
      contentType: file.type || 'application/octet-stream',
      size: file.size,
      objectKey: `mock/attachments/${file.name}`,
      scanStatus: infected ? 'INFECTED' : 'CLEAN',
      scanEngine: 'content-signature-v1',
      scanDetail: infected ? 'EICAR marker in filename' : 'ok',
    };
    mockStore.set(meta.id, meta);
    return meta;
  }

  const form = new FormData();
  form.append('file', file, file.name);
  // Intentionally omit Content-Type — browser sets multipart boundary.
  // apiFetch attaches Bearer and retries once after OIDC refresh on 401.
  const res = await apiFetch('/attachments', {
    method: 'POST',
    body: form,
  });

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }

  return (await res.json()) as AttachmentMeta;
}

export async function getAttachment(id: string): Promise<AttachmentMeta | null> {
  if (isMockMode()) {
    await delay(120);
    return mockStore.get(id) ?? null;
  }

  const res = await apiFetch(`/attachments/${encodeURIComponent(id)}`);
  if (res.status === 404) return null;
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }
  return (await res.json()) as AttachmentMeta;
}

/** Absolute or relative URL to download attachment content */
export function getContentUrl(id: string): string {
  return `${getBaseUrl()}/attachments/${encodeURIComponent(id)}/content`;
}

/** List attachments linked to a work item (persisted). */
export async function listWorkItemAttachments(
  workItemId: string,
): Promise<AttachmentMeta[]> {
  if (isMockMode()) {
    await delay(160);
    const ids = mockLinks.get(workItemId) ?? [];
    return ids
      .map((id) => mockStore.get(id))
      .filter((m): m is AttachmentMeta => m != null);
  }
  const list = await apiRequest<
    Array<{
      id: string;
      filename: string;
      contentType: string;
      size: number;
      objectKey: string;
      linkedBy?: string;
      linkedAt?: string;
    }>
  >(`/work-items/${encodeURIComponent(workItemId)}/attachments`);
  return (list ?? []).map((a) => ({
    id: String(a.id),
    filename: a.filename,
    contentType: a.contentType,
    size: a.size,
    objectKey: a.objectKey,
    linkedBy: a.linkedBy,
    linkedAt: a.linkedAt,
    scanStatus: (a as { scanStatus?: AttachmentScanStatus }).scanStatus,
    scanEngine: (a as { scanEngine?: string }).scanEngine,
  }));
}

/** Link an existing attachment to a work item. */
export async function linkWorkItemAttachment(
  workItemId: string,
  attachmentId: string,
): Promise<AttachmentMeta> {
  if (isMockMode()) {
    await delay(120);
    const meta = mockStore.get(attachmentId);
    if (!meta) throw new ApiError(404, 'Attachment not found');
    const ids = mockLinks.get(workItemId) ?? [];
    if (!ids.includes(attachmentId)) {
      mockLinks.set(workItemId, [attachmentId, ...ids]);
    }
    return { ...meta, linkedAt: new Date().toISOString() };
  }
  const a = await apiRequest<{
    id: string;
    filename: string;
    contentType: string;
    size: number;
    objectKey: string;
    linkedBy?: string;
    linkedAt?: string;
  }>(`/work-items/${encodeURIComponent(workItemId)}/attachments`, {
    method: 'POST',
    body: { attachmentId },
  });
  return {
    id: String(a.id),
    filename: a.filename,
    contentType: a.contentType,
    size: a.size,
    objectKey: a.objectKey,
    linkedBy: a.linkedBy,
    linkedAt: a.linkedAt,
  };
}

/** Unlink attachment from work item (does not delete blob). */
export async function unlinkWorkItemAttachment(
  workItemId: string,
  attachmentId: string,
): Promise<void> {
  if (isMockMode()) {
    await delay(100);
    const ids = mockLinks.get(workItemId) ?? [];
    mockLinks.set(
      workItemId,
      ids.filter((id) => id !== attachmentId),
    );
    return;
  }
  await apiRequest<void>(
    `/work-items/${encodeURIComponent(workItemId)}/attachments/${encodeURIComponent(attachmentId)}`,
    { method: 'DELETE' },
  );
}

/**
 * Upload file and link to work item in one operator step.
 * Live: POST /attachments then POST /work-items/{id}/attachments.
 * Mock: store + link map.
 */
export async function uploadAndLinkWorkItemAttachment(
  workItemId: string,
  file: File,
): Promise<AttachmentMeta> {
  const meta = await uploadAttachment(file);
  return linkWorkItemAttachment(workItemId, meta.id);
}

/** Format byte size for chips */
export function formatBytes(size: number): string {
  if (!Number.isFinite(size) || size < 0) return '—';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(size < 10 * 1024 ? 1 : 0)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}
