import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

test('host dark theme matches NOUS Android Material 3 roles', () => {
  const tokens = JSON.parse(readFileSync(new URL('../design/tokens.json', import.meta.url), 'utf8'));
  const android = readFileSync(new URL('../../../../../android/app/src/main/res/values/nous.xml', import.meta.url), 'utf8');
  const colors = tokens.primitive.color.dark;
  for (const [role, name] of Object.entries({
    canvas: 'background', surface: 'surface', subtle: 'surface_high',
    textPrimary: 'text', textSecondary: 'text_muted', borderSubtle: 'outline',
    accentDefault: 'primary', onAccent: 'on_primary', info: 'secondary',
  })) {
    const match = android.match(new RegExp(`<color name="nous_${name}">(#[\\da-f]+)</color>`, 'i'));
    assert.ok(match, `Android color nous_${name} must exist`);
    assert.equal(colors[role].$value.toUpperCase(), match[1].toUpperCase(), role);
  }
});
