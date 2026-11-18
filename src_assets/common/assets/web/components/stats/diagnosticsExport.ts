import type { SessionSummary } from '../../types/sessions';

/**
 * Public diagnostics export contract.
 *
 * Only an explicit allowlist of session-summary fields is exported. Raw
 * samples, event payloads, authentication state, tokens, headers, and host
 * network identities are intentionally excluded.
 */
export const DIAGNOSTICS_EXPORT_SCHEMA = 'nous.diagnostics.session-history.v1';

export interface DiagnosticsHostIdentity {
  cpu_model?: string;
  gpu_model?: string;
  cpu_logical_cores?: number;
  ram_total_bytes?: number;
  vram_total_bytes?: number;
}

export interface DiagnosticsHistoryStatus {
  available?: boolean;
  degraded?: boolean;
}

export interface DiagnosticsExportInput {
  sessions: SessionSummary[];
  host?: DiagnosticsHostIdentity | null;
  historyStatus?: DiagnosticsHistoryStatus | null;
  /** True when the local export cap stopped the pagination early. */
  truncated?: boolean;
  generatedAt?: Date;
}

export interface DiagnosticsExport {
  schema: string;
  generated_at: string;
  source: 'nous-host';
  /**
   * Each entry is `true` when that class of data is excluded. These are fixed
   * guarantees of the exporter, not values read from the host.
   */
  excluded: {
    credentials: true;
    raw_event_payloads: true;
    network_identities: true;
  };
  history: {
    /** `null` when the host did not report history availability. */
    available: boolean | null;
    degraded: boolean;
    truncated: boolean;
  };
  host: DiagnosticsHostIdentity | null;
  session_count: number;
  sessions: Array<Record<string, unknown>>;
}

function stringOrUndefined(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function numberOrUndefined(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function booleanOrUndefined(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

function sanitizeHost(host: DiagnosticsHostIdentity | null | undefined): DiagnosticsHostIdentity | null {
  if (!host || typeof host !== 'object') return null;
  const clean: DiagnosticsHostIdentity = {};
  const cpu = stringOrUndefined(host.cpu_model);
  const gpu = stringOrUndefined(host.gpu_model);
  const cores = numberOrUndefined(host.cpu_logical_cores);
  const ram = numberOrUndefined(host.ram_total_bytes);
  const vram = numberOrUndefined(host.vram_total_bytes);
  if (cpu) clean.cpu_model = cpu;
  if (gpu) clean.gpu_model = gpu;
  if (cores != null) clean.cpu_logical_cores = cores;
  if (ram != null) clean.ram_total_bytes = ram;
  if (vram != null) clean.vram_total_bytes = vram;
  return Object.keys(clean).length ? clean : null;
}

/** Copies only the approved summary fields; unknown fields are dropped. */
export function sanitizeSessionSummary(session: SessionSummary): Record<string, unknown> {
  const clean: Record<string, unknown> = {};
  const strings = [
    'uuid',
    'protocol',
    'client_name',
    'device_name',
    'app_name',
    'codec',
    'verdict',
    'server_version',
    'host_cpu_model',
    'host_gpu_model',
    'stream_gpu_model',
  ] as const;
  const numbers = [
    'width',
    'height',
    'target_fps',
    'encoder_bitrate_kbps',
    'requested_bitrate_kbps',
    'audio_channels',
    'start_time_unix',
    'end_time_unix',
    'duration_seconds',
  ] as const;
  const booleans = ['hdr', 'yuv444'] as const;

  for (const key of strings) {
    const value = stringOrUndefined(session[key]);
    if (value !== undefined) clean[key] = value;
  }
  for (const key of numbers) {
    const value = numberOrUndefined(session[key]);
    if (value !== undefined) clean[key] = value;
  }
  for (const key of booleans) {
    const value = booleanOrUndefined(session[key]);
    if (value !== undefined) clean[key] = value;
  }
  return clean;
}

export function buildDiagnosticsExport(input: DiagnosticsExportInput): DiagnosticsExport {
  const sessions = input.sessions.map(sanitizeSessionSummary);
  return {
    schema: DIAGNOSTICS_EXPORT_SCHEMA,
    generated_at: (input.generatedAt ?? new Date()).toISOString(),
    source: 'nous-host',
    excluded: {
      credentials: true,
      raw_event_payloads: true,
      network_identities: true,
    },
    history: {
      available:
        typeof input.historyStatus?.available === 'boolean'
          ? input.historyStatus.available
          : null,
      degraded: input.historyStatus?.degraded === true,
      truncated: input.truncated === true,
    },
    host: sanitizeHost(input.host),
    session_count: sessions.length,
    sessions,
  };
}

export function diagnosticsExportFilename(date = new Date()): string {
  const stamp = date.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return `nous-diagnostics-${stamp}.json`;
}
