import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

// Execute production functions without loading browser-only imports or changing their exports.
function loadFunctions(path: string, names: string[]): Record<string, (...args: unknown[]) => void> {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8');
  const functions = names.map((name) => {
    const match = source.match(new RegExp(`^function ${name}\\([\\s\\S]*?^}`, 'm'));
    assert.ok(match, `Production function ${name} must exist`);
    return match[0];
  });
  return runInNewContext(
    stripTypeScriptTypes(`${functions.join('\n')}\n({ ${names.join(', ')} });`),
  );
}

const modern = loadFunctions('../services/webrtc.ts', ['applyReceiverLatencyHints']);
const legacy = loadFunctions('../../web-legacy/utils/webrtc/client.ts', [
  'resolveJitterTargetMs',
  'applyAudioReceiverHints',
  'applyVideoReceiverHints',
]);
const implementations = [
  ['modern', modern.applyReceiverLatencyHints],
  ['legacy audio', legacy.applyAudioReceiverHints],
  ['legacy video', legacy.applyVideoReceiverHints],
] as const;

for (const [name, apply] of implementations) {
  for (const initial of [null, 45]) {
    test(`${name}: existing jitterBufferTarget accepts target when initial value is ${initial}`, () => {
      const receiver = { jitterBufferTarget: initial, playoutDelayHint: null };
      apply(receiver, 20, 20);
      assert.equal(receiver.jitterBufferTarget, 20);
      assert.equal(receiver.playoutDelayHint, 0.02);
      apply(receiver, 0, 0);
      assert.equal(receiver.jitterBufferTarget, 0);
      assert.equal(receiver.playoutDelayHint, 0);
    });
  }

  test(`${name}: absent API stays absent`, () => {
    const receiver = {};
    assert.doesNotThrow(() => apply(receiver, 20, 20));
    assert.deepEqual(receiver, {});
    assert.doesNotThrow(() => apply(undefined, 20, 20));
  });

  test(`${name}: inherited API needs no getter read; throwing setter stays optional`, () => {
    let attempts = 0;
    const receiver = Object.create({
      get jitterBufferTarget() { throw new Error('Getter must not be used for feature detection'); },
      set jitterBufferTarget(value: number) {
        assert.equal(value, 20);
        attempts++;
        throw new Error('Setter unavailable');
      },
      playoutDelayHint: null,
    });
    assert.doesNotThrow(() => apply(receiver, 20, 20));
    assert.equal(attempts, 1);
    assert.equal(receiver.playoutDelayHint, 0.02);
  });
}

for (const name of ['applyAudioReceiverHints', 'applyVideoReceiverHints']) {
  test(`${name}: omitted target preserves legacy defaults`, () => {
    const receiver = { jitterBufferTarget: null, playoutDelayHint: null };
    legacy[name](receiver);
    assert.deepEqual(receiver, { jitterBufferTarget: null, playoutDelayHint: null });
  });
}
