// Run: node .github/scripts/test_manual_workflow_policy.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const workflows = path.join(root, '.github/workflows');
let reusable = 0;
for (const name of fs.readdirSync(workflows).filter(name => /\.ya?ml$/.test(name))) {
  const text = fs.readFileSync(path.join(workflows, name), 'utf8');
  const trigger = text.match(/^on:\r?\n((?:(?:[ \t].*|)\r?\n)*)/m);
  assert.ok(trigger, `${name}: expected block-form on`);
  const events = [...trigger[1].matchAll(/^  ([\w_]+):/gm)].map(match => match[1]);
  assert.ok(events.length, `${name}: no trigger`);
  assert.ok(events.every(event => ['workflow_dispatch', 'workflow_call'].includes(event)),
    `${name}: automatic trigger: ${events.join(', ')}`);
  if (events.includes('workflow_call')) reusable++;
}
assert.equal(reusable, 7, 'All seven platform reusable workflow interfaces must remain callable');
const linux = fs.readFileSync(path.join(workflows, 'ci-linux.yml'), 'utf8');
assert.ok(linux.includes('ICON_FILE="${ICON_FILE:-nous.svg}"'));
const pages = fs.readFileSync(path.join(workflows, 'update-pages.yml'), 'utf8');
assert.ok(pages.includes('ref: nous-project'));
const daily = fs.readFileSync(path.join(workflows, 'daily-prerelease.yml'), 'utf8');
assert.ok(daily.includes("github.ref_name != 'nous-project'"), 'Launcher must not dispatch itself when nous-project is default');
assert.ok(daily.includes("flugeru/NOUS:nous-project"));
assert.ok(!daily.includes('--product Vibepollo'));
const signing = fs.readFileSync(path.join(workflows, 'sign-release.yml'), 'utf8');
assert.ok(signing.includes('ci.yml/runs?event=workflow_dispatch&status=completed'));
assert.ok(signing.includes('"${source_event}" != "workflow_dispatch"'));
assert.ok(signing.includes('.head_branch == $source_tag'), 'Manual release build must retain exact tag provenance');
const windows = fs.readFileSync(path.join(workflows, 'ci-windows.yml'), 'utf8');
assert.ok(windows.includes("'.\\build\\NOUS.exe'"), 'Generate symbols for NOUS host');
assert.ok(windows.includes("'NOUS.exe','sunshine.exe'"), 'Verify NOUS host and shipped compatibility alias');
assert.ok(!/Apollo[/\\]drivers[/\\]/.test(windows), 'Extract MSI payload under NOUS root');
assert.ok(windows.includes('VibepolloPlaynite.dll'), 'Preserve unchanged Playnite output name');
const portable = fs.readFileSync(path.join(root, '.github/scripts/validate_windows_portable.py'), 'utf8');
assert.ok(portable.includes('stage / "NOUS.exe"'), 'Smoke primary portable host');
console.log('PASS: manual-only workflows, reusable interfaces, NOUS assets, source gates and Windows output contract');
