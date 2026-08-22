/**
 * Mock announcement store, shaped like backend `AnnouncementService.Announcement`.
 * Dev only — the live API never reads this module.
 */
import type {
  Announcement,
  AnnouncementAudience,
  AnnouncementInput,
} from '@/types';

const SEVERITY_ORDER: Record<Announcement['severity'], number> = {
  CRITICAL: 0,
  WARNING: 1,
  INFO: 2,
};

let sequence = 0;

const announcements: Announcement[] = [
  {
    id: 'ann-1',
    title: 'Payment gateway degraded',
    body: 'Card payments fail intermittently. Link new incidents to PRB-000042 instead of opening duplicates.',
    severity: 'CRITICAL',
    audience: 'AGENTS',
    startsAt: new Date(Date.now() - 3_600_000).toISOString(),
    endsAt: null,
    published: true,
    dismissible: true,
    linkUrl: null,
    createdBy: 'carol',
    createdAt: new Date(Date.now() - 3_600_000).toISOString(),
    updatedAt: new Date(Date.now() - 3_600_000).toISOString(),
    version: 0,
  },
];

function mustFind(id: string): Announcement {
  const found = announcements.find((entry) => entry.id === id);
  if (!found) throw new Error(`Announcement not found: ${id}`);
  return found;
}

function validate(input: AnnouncementInput): void {
  if (!input.title.trim()) throw new Error('title is required');
  if (!input.body.trim()) throw new Error('body is required');
  if (!input.startsAt) throw new Error('startsAt is required');
  if (input.endsAt && new Date(input.endsAt).getTime() <= new Date(input.startsAt).getTime()) {
    throw new Error('endsAt must be after startsAt');
  }
}

export function listMockAnnouncements(): Announcement[] {
  return [...announcements]
    .sort((a, b) => new Date(b.startsAt).getTime() - new Date(a.startsAt).getTime())
    .map((entry) => ({ ...entry }));
}

export function activeMockAnnouncements(
  audience: AnnouncementAudience,
  at: string,
): Announcement[] {
  const when = new Date(at).getTime();
  return announcements
    .filter(
      (entry) =>
        entry.published &&
        new Date(entry.startsAt).getTime() <= when &&
        (entry.endsAt === null || new Date(entry.endsAt).getTime() > when) &&
        (entry.audience === 'ALL' || entry.audience === audience),
    )
    .sort(
      (a, b) =>
        SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] ||
        new Date(b.startsAt).getTime() - new Date(a.startsAt).getTime(),
    )
    .map((entry) => ({ ...entry }));
}

export function createMockAnnouncement(input: AnnouncementInput): Announcement {
  validate(input);
  sequence += 1;
  const now = new Date().toISOString();
  const created: Announcement = {
    id: `ann-new-${sequence}`,
    title: input.title.trim(),
    body: input.body.trim(),
    severity: input.severity,
    audience: input.audience,
    startsAt: input.startsAt,
    endsAt: input.endsAt ?? null,
    published: input.published,
    dismissible: input.dismissible,
    linkUrl: input.linkUrl ?? null,
    createdBy: 'dev-local',
    createdAt: now,
    updatedAt: now,
    version: 0,
  };
  announcements.unshift(created);
  return { ...created };
}

export function updateMockAnnouncement(id: string, input: AnnouncementInput): Announcement {
  validate(input);
  const found = mustFind(id);
  Object.assign(found, {
    title: input.title.trim(),
    body: input.body.trim(),
    severity: input.severity,
    audience: input.audience,
    startsAt: input.startsAt,
    endsAt: input.endsAt ?? null,
    published: input.published,
    dismissible: input.dismissible,
    linkUrl: input.linkUrl ?? null,
    updatedAt: new Date().toISOString(),
    version: found.version + 1,
  });
  return { ...found };
}

export function retireMockAnnouncement(id: string): Announcement {
  const found = mustFind(id);
  found.endsAt = new Date().toISOString();
  found.version += 1;
  found.updatedAt = found.endsAt;
  return { ...found };
}

export function deleteMockAnnouncement(id: string): void {
  const index = announcements.findIndex((entry) => entry.id === id);
  if (index < 0) throw new Error(`Announcement not found: ${id}`);
  announcements.splice(index, 1);
}
