import { delay, useMock, apiRequest } from './client';
import { getQueueCopilotStats } from '@/mock/store';

export interface CopilotSuggestion {
  provider: string;
  model: string;
  content: string;
  citations: string[];
  requiresHumanReview: boolean;
}

export interface SummarizePayload {
  content: string;
  maxTokens?: number;
  scopes?: string[];
}

/** Build a free-form queue summary for the copilot summarize endpoint. */
export function buildQueueSummaryText(stats: {
  open: number;
  breached: number;
  atRisk: number;
  unassigned: number;
  critical: number;
  topBreached: Array<{ number: string; title: string; slaTarget: string }>;
}): string {
  const lines = [
    'ITSM operator queue snapshot for shift briefing.',
    `Open work items: ${stats.open}.`,
    `SLA breached: ${stats.breached}.`,
    `SLA at risk: ${stats.atRisk}.`,
    `Unassigned: ${stats.unassigned}.`,
    `Critical priority: ${stats.critical}.`,
  ];
  if (stats.topBreached.length > 0) {
    lines.push('Top breached:');
    for (const b of stats.topBreached) {
      lines.push(`- ${b.number}: ${b.title} (SLA ${b.slaTarget})`);
    }
  }
  lines.push(
    'Provide a concise operator briefing: priorities, risks, and recommended next actions.',
  );
  return lines.join('\n');
}

function buildMockCopilotContent(): string {
  const s = getQueueCopilotStats();
  const parts: string[] = [];

  if (s.breached > 0) {
    parts.push(
      `${s.breached} item(s) already breached SLA — treat these as the first triage batch before new intake.`,
    );
    if (s.topBreached[0]) {
      const top = s.topBreached[0];
      parts.push(
        `Hottest ticket: ${top.number} “${top.title}” (clock ${top.slaTarget}).`,
      );
    }
  } else {
    parts.push('No breached SLAs right now — keep pressure on at-risk items.');
  }

  if (s.atRisk > 0) {
    parts.push(
      `${s.atRisk} at-risk item(s) will tip into breach if ownership stays thin; pull from “At risk” filter next.`,
    );
  }

  if (s.unassigned > 0) {
    parts.push(
      `${s.unassigned} unassigned — claim or re-route before they age past first response.`,
    );
  }

  if (s.critical > 0) {
    parts.push(
      `${s.critical} critical priority ticket(s) open across ${s.open} active items.`,
    );
  }

  if (parts.length === 0) {
    parts.push(
      `Queue is calm: ${s.open} open items, no urgent SLA pressure. Good window for backlog hygiene.`,
    );
  } else {
    parts.push(
      `Across ${s.open} open items: focus breached → at-risk → unassigned, then critical backlog.`,
    );
  }

  return parts.join(' ');
}

/**
 * POST /api/v1/ai/copilot/summarize
 * Mock: scripted briefing from durable mock store stats.
 */
export async function summarizeCopilot(
  payload?: Partial<SummarizePayload>,
): Promise<CopilotSuggestion> {
  if (useMock()) {
    await delay(420);
    return {
      provider: 'mock',
      model: 'vox-operator-brief-v1',
      content: buildMockCopilotContent(),
      citations: ['mock://queue-stats'],
      requiresHumanReview: true,
    };
  }

  const stats = {
    open: 0,
    breached: 0,
    atRisk: 0,
    unassigned: 0,
    critical: 0,
    topBreached: [] as Array<{
      number: string;
      title: string;
      slaTarget: string;
    }>,
  };
  // Live path: use caller-provided content when available
  const content =
    payload?.content?.trim() ||
    buildQueueSummaryText(stats);

  return apiRequest<CopilotSuggestion>('/ai/copilot/summarize', {
    method: 'POST',
    body: {
      content,
      maxTokens: payload?.maxTokens ?? 512,
      scopes: payload?.scopes ?? ['queue', 'sla'],
    },
  });
}
