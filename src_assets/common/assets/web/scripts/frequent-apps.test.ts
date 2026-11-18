import assert from 'node:assert/strict';
import test from 'node:test';
import { rankFrequentApps } from '../utils/frequentApps.ts';

const NOW = 1_800_000_000;

function session(app: string, started: number, uuid: string) {
  return {
    uuid,
    protocol: 'rtsp',
    client_name: 'Client',
    device_name: 'Device',
    app_name: app,
    codec: 'hevc',
    width: 1920,
    height: 1080,
    target_fps: 60,
    encoder_bitrate_kbps: 20000,
    audio_channels: 2,
    hdr: false,
    start_time_unix: started,
    duration_seconds: 60,
  };
}

test('ranks by completed sessions inside the retention window', () => {
  const result = rankFrequentApps(
    [
      session('Portal', NOW - 100, 'a'),
      session('Portal', NOW - 200, 'b'),
      session('Portal 2', NOW - 300, 'c'),
      session('Old', NOW - 40 * 86400, 'd'),
    ],
    { nowUnix: NOW },
  );
  assert.deepEqual(
    result.map((item) => item.name),
    ['Portal', 'Portal 2'],
  );
  assert.equal(result[0]?.sessions, 2);
});

test('normalizes names, retains the newest spelling, and breaks ties by recency', () => {
  const result = rankFrequentApps(
    [
      session('  portal  ', NOW - 300, 'a'),
      session('PORTAL', NOW - 100, 'b'),
      session('Portal 2', NOW - 50, 'c'),
    ],
    { nowUnix: NOW },
  );
  assert.equal(result[0]?.name, 'PORTAL');
  assert.equal(result[0]?.sessions, 2);
  assert.equal(result[1]?.name, 'Portal 2');
});

test('ignores empty names, invalid timestamps, and respects the limit', () => {
  const result = rankFrequentApps(
    [
      session('   ', NOW - 10, 'a'),
      { ...session('Broken', NOW, 'b'), start_time_unix: Number.NaN },
      session('One', NOW - 10, 'c'),
      session('Two', NOW - 20, 'd'),
      session('Three', NOW - 30, 'e'),
    ],
    { nowUnix: NOW, limit: 2 },
  );
  assert.deepEqual(
    result.map((item) => item.name),
    ['One', 'Two'],
  );
  assert.deepEqual(rankFrequentApps([], { nowUnix: NOW }), []);
});
