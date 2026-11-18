import type { SessionSummary } from '../types/sessions';

export interface FrequentAppSuggestion {
  /** Display name from the most recent matching session. */
  name: string;
  /** Number of completed sessions in the window. */
  sessions: number;
  /** Most recent session start, in Unix seconds. */
  lastUsedUnix: number;
}

export interface RankFrequentAppsOptions {
  limit?: number;
  /** Window in days. Defaults to the diagnostics retention window. */
  days?: number;
  /** Clock override for deterministic tests. */
  nowUnix?: number;
}

function normalizeAppName(value: unknown): string {
  return typeof value === 'string'
    ? value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase()
    : '';
}

/**
 * Ranks apps by completed session count. This only suggests apps that
 * already belong to the library; callers must map names back to their own
 * records and never add anything automatically.
 */
export function rankFrequentApps(
  sessions: SessionSummary[],
  options: RankFrequentAppsOptions = {},
): FrequentAppSuggestion[] {
  const limit = Math.max(0, Math.floor(options.limit ?? 4));
  const days = Math.max(1, options.days ?? 30);
  const nowUnix = options.nowUnix ?? Date.now() / 1000;
  const cutoff = nowUnix - days * 86400;

  const byName = new Map<string, FrequentAppSuggestion>();
  for (const session of sessions) {
    const key = normalizeAppName(session.app_name);
    if (!key) continue;
    const started = Number(session.start_time_unix);
    if (!Number.isFinite(started) || started < cutoff) continue;

    const current = byName.get(key);
    if (!current) {
      byName.set(key, {
        name: session.app_name.trim(),
        sessions: 1,
        lastUsedUnix: started,
      });
      continue;
    }
    current.sessions += 1;
    if (started >= current.lastUsedUnix) {
      current.lastUsedUnix = started;
      current.name = session.app_name.trim();
    }
  }

  return [...byName.values()]
    .sort(
      (left, right) =>
        right.sessions - left.sessions ||
        right.lastUsedUnix - left.lastUsedUnix ||
        left.name.localeCompare(right.name),
    )
    .slice(0, limit);
}
