import { readFileSync } from 'node:fs';

const root = new URL('../public/assets/locale/', import.meta.url);
export function flatten(value, prefix = '') {
  return Object.fromEntries(Object.entries(value).flatMap(([key, entry]) =>
    entry && typeof entry === 'object' && !Array.isArray(entry)
      ? Object.entries(flatten(entry, `${prefix}${key}.`))
      : [[`${prefix}${key}`, entry]]));
}
const read = (path) => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
for (const [source, target] of [['en.json', 'pt_BR.json'], ['ui/en.json', 'ui/pt_BR.json']]) {
  const english = flatten(read(source));
  const portuguese = flatten(read(target));
  const missing = Object.keys(english).filter((key) => !Object.hasOwn(portuguese, key));
  console.log(`${target}: ${Object.keys(english).length - missing.length}/${Object.keys(english).length} keys; ${missing.length} missing`);
  if (process.argv.includes('--missing')) for (const key of missing) console.log(`${key}\t${english[key]}`);
}
