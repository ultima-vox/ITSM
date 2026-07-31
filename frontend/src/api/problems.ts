import { delay, useMock, apiRequest } from './client';
import { mapProblem, type BackendProblemSummary } from './mappers/problems';
import { problems } from '@/mock/data';
import type { Problem } from '@/types';

export async function fetchProblems(): Promise<Problem[]> {
  if (useMock()) {
    await delay(220);
    return problems;
  }
  const list = await apiRequest<BackendProblemSummary[]>('/problems');
  return (list ?? []).map(mapProblem);
}
