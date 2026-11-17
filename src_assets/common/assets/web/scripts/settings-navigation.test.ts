import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('credentials stay reachable inside Settings without a primary navigation entry', () => {
  assert.doesNotMatch(source('../components/layout/AppShell.vue'), /to: '\/api-tokens'/);
  assert.match(source('../views/SettingsView.vue'), /<RouterLink[^>]+to="\/api-tokens"/);
  assert.match(source('../router.ts'), /path: '\/api-tokens'/);
});
