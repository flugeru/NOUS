import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import test from 'node:test';

const catalog = (name: string) => JSON.parse(readFileSync(new URL(`../public/assets/locale/${name}.json`, import.meta.url), 'utf8'));
function flatten(value: Record<string, unknown>, prefix = ''): Record<string, string> {
  return Object.fromEntries(Object.entries(value).flatMap(([key, entry]) =>
    typeof entry === 'object' && entry !== null
      ? Object.entries(flatten(entry as Record<string, unknown>, `${prefix}${key}.`))
      : [[`${prefix}${key}`, String(entry)]]));
}

test('default host entry opens unified UI and retains explicit classic fallback', () => {
  const server = readFileSync(new URL('../../../../../src/confighttp.cpp', import.meta.url), 'utf8');
  const shell = readFileSync(new URL('../components/layout/AppShell.vue', import.meta.url), 'utf8');
  assert.match(server, /path_view == "\/"[\s\S]*?query\.find\("classic"\)[\s\S]*?send_redirect\(response, request, "\/v2\/"\)/);
  const settings = readFileSync(new URL('../views/SettingsView.vue', import.meta.url), 'utf8');
  assert.doesNotMatch(shell, /href="\/\?classic=1"/);
  assert.match(settings, /<details\b[^>]*>[\s\S]*?href="\/\?classic=1"[\s\S]*?<\/details>/);
});

test('literal UI translation calls reference existing keys', () => {
  const root = new URL('../', import.meta.url);
  const english = { ...flatten(catalog('en')), ...flatten(catalog('ui/en')) };
  const missing: string[] = [];
  for (const file of readdirSync(root, { recursive: true }) as string[]) {
    if (!/^(components|views|composables|stores|configs)[\\/].*\.(vue|ts)$/.test(file)) continue;
    const source = readFileSync(new URL(file.replaceAll('\\', '/'), root), 'utf8');
    for (const match of source.matchAll(/\bt\(\s*['"](ui\.[\w.]+)['"]/g)) {
      if (!Object.hasOwn(english, match[1])) missing.push(`${file}: ${match[1]}`);
    }
  }
  assert.deepEqual(missing, []);
});

for (const prefix of ['', 'ui/']) {
  test(`PT-BR ${prefix || 'base'} covers every English key and preserves parameters and references`, () => {
    const english = flatten(catalog(`${prefix}en`));
    const portuguese = flatten(catalog(`${prefix}pt_BR`));
    const merged = { ...flatten(catalog('pt_BR')), ...flatten(catalog('ui/pt_BR')) };
    for (const [key, text] of Object.entries(english)) {
      assert.ok(Object.hasOwn(portuguese, key), key);
      assert.ok(portuguese[key].trim(), `${key}: empty translation`);
      // Plural forms may repeat parameters a different number of times.
      const parameters = (value: string) => [...new Set(value.match(/\{\w+\}/g) ?? [])].sort();
      assert.deepEqual(parameters(portuguese[key]), parameters(text), `${key}: parameters`);
      const references = (value: string) => value.match(/@:[\w.]+/g) ?? [];
      assert.deepEqual(references(portuguese[key]), references(text), `${key}: references`);
      for (const reference of references(portuguese[key])) {
        assert.ok(Object.hasOwn(merged, reference.slice(2)), `${key}: missing ${reference}`);
      }
      assert.equal(portuguese[key].split('|').length, text.split('|').length, `${key}: plural forms`);
    }
  });
}
