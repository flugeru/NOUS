import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DIAGNOSTICS_EXPORT_SCHEMA,
  buildDiagnosticsExport,
  diagnosticsExportFilename,
  sanitizeSessionSummary,
} from '../components/stats/diagnosticsExport.ts';

const baseSession = {
  uuid: '11111111-2222-4333-8444-555555555555',
  protocol: 'rtsp',
  client_name: 'Tablet',
  device_name: 'Living room',
  app_name: 'Portal',
  codec: 'hevc',
  verdict: 'healthy',
  width: 1920,
  height: 1080,
  target_fps: 60,
  encoder_bitrate_kbps: 20000,
  requested_bitrate_kbps: 25000,
  audio_channels: 2,
  hdr: false,
  yuv444: false,
  server_version: '1.0.390.579',
  start_time_unix: 1700000000,
  end_time_unix: 1700003600,
  duration_seconds: 3600,
  host_cpu_model: 'CPU',
  host_gpu_model: 'GPU',
  stream_gpu_model: 'GPU',
  // Fields outside the public contract must never be exported.
  session_token: 'secret-token',
  auth_header: 'Bearer secret',
  client_ip: '192.168.1.50',
} as const;

test('diagnostics export uses an explicit allowlist and drops credentials', () => {
  const clean = sanitizeSessionSummary(baseSession as never);
  assert.equal(clean.uuid, baseSession.uuid);
  assert.equal(clean.app_name, 'Portal');
  assert.equal(clean.session_token, undefined);
  assert.equal(clean.auth_header, undefined);
  assert.equal(clean.client_ip, undefined);
});

test('diagnostics export records schema and redaction guarantees', () => {
  const payload = buildDiagnosticsExport({
    sessions: [baseSession as never],
    host: {
      cpu_model: 'CPU',
      gpu_model: 'GPU',
      cpu_logical_cores: 16,
      ram_total_bytes: 32_000_000_000,
      // Unknown host keys are dropped.
      hostname: 'private-host',
      ip: '10.0.0.2',
    } as never,
    generatedAt: new Date('2026-11-18T12:00:00Z'),
  });
  assert.equal(payload.schema, DIAGNOSTICS_EXPORT_SCHEMA);
  assert.equal(payload.generated_at, '2026-11-18T12:00:00.000Z');
  assert.equal(payload.excluded.credentials, true);
  assert.equal(payload.excluded.raw_event_payloads, true);
  assert.equal(payload.excluded.network_identities, true);
  assert.equal(payload.history.available, null);
  assert.equal(payload.history.degraded, false);
  assert.equal(payload.history.truncated, false);
  assert.equal(payload.session_count, 1);
  assert.equal(payload.host?.cpu_model, 'CPU');
  assert.equal((payload.host as Record<string, unknown>).hostname, undefined);
  assert.equal((payload.host as Record<string, unknown>).ip, undefined);
});

test('diagnostics export handles explicit absence and stable filenames', () => {
  const payload = buildDiagnosticsExport({
    sessions: [],
    host: null,
    historyStatus: { available: false, degraded: true },
    truncated: true,
    generatedAt: new Date('2026-11-18T12:34:56Z'),
  });
  assert.equal(payload.session_count, 0);
  assert.deepEqual(payload.sessions, []);
  assert.equal(payload.host, null);
  assert.equal(payload.history.available, false);
  assert.equal(payload.history.degraded, true);
  assert.equal(payload.history.truncated, true);
  assert.equal(diagnosticsExportFilename(new Date('2026-11-18T12:34:56Z')), 'nous-diagnostics-2026-11-18T12-34-56.json');
});
