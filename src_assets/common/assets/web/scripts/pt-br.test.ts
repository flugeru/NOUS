import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const catalog = (name: string) => JSON.parse(readFileSync(new URL(`../public/assets/locale/ui/${name}.json`, import.meta.url), 'utf8'));
function flatten(value: Record<string, unknown>, prefix = ''): Record<string, string> {
  return Object.fromEntries(Object.entries(value).flatMap(([key, entry]) =>
    typeof entry === 'object' && entry !== null
      ? Object.entries(flatten(entry as Record<string, unknown>, `${prefix}${key}.`))
      : [[`${prefix}${key}`, String(entry)]]));
}

test('PT-BR core screens cover English keys and preserve interpolation parameters', () => {
  const english = catalog('en');
  const portuguese = catalog('pt_BR');
  for (const section of ['app', 'auth', 'common', 'nav', 'shell', 'status', 'system', 'pair', 'overview', 'library', 'devices', 'browser_stream', 'logs', 'sessions', 'maintenance', 'api_tokens', 'integrations']) {
    const expected = flatten(english.ui[section]);
    const actual = flatten(portuguese.ui[section]);
    assert.deepEqual(Object.keys(actual).sort(), Object.keys(expected).sort(), section);
    for (const [key, text] of Object.entries(expected)) {
      assert.deepEqual(actual[key].match(/\{\w+\}/g)?.sort() ?? [], text.match(/\{\w+\}/g)?.sort() ?? [], `${section}.${key}`);
    }
  }
});
