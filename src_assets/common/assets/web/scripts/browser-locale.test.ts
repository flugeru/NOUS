import assert from 'node:assert/strict';
import test from 'node:test';
import { browserLocale } from '../utils/browserLocale.ts';

test('browser language priority selects available catalogs without host configuration', () => {
  assert.equal(browserLocale(['pt-BR', 'en-US']), 'pt_BR');
  assert.equal(browserLocale(['pt-PT']), 'pt');
  assert.equal(browserLocale(['fr-CA', 'pt-BR']), 'fr');
  assert.equal(browserLocale(['xx', 'pt-BR']), 'pt_BR');
  assert.equal(browserLocale(['zh-Hant-HK']), 'zh_TW');
  assert.equal(browserLocale(['en-US']), 'en_US');
  assert.equal(browserLocale(['not-a-locale']), 'en');
  assert.equal(browserLocale([]), 'en');
});
