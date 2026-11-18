/**
 * Pure helpers for the public SteamGridDB artwork proxy.
 *
 * SteamGridDB returns 920x430 grids and wide heroes; none of those files are
 * natively 16:9. The UI presents them in a 16:9 frame by cropping, and these
 * helpers never relabel the source dimensions.
 */

export interface SteamGridGame {
  id: number;
  name: string;
  verified?: boolean;
  types?: string[];
  release_date?: number;
}

export interface SteamGridAsset {
  id: number;
  url: string;
  thumb?: string;
  width?: number;
  height?: number;
  style?: string;
  mime?: string;
  nsfw?: boolean;
  humor?: boolean;
}

export interface SteamGridCandidate {
  name: string;
  key: string;
  url: string;
  saveUrl: string;
  aspect: 'wide';
  source: 'steamgriddb';
}

const MAX_CANDIDATES = 24;

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function integer(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null;
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function cdnUrl(value: unknown): string {
  const url = text(value);
  return url.startsWith('https://cdn2.steamgriddb.com/') && url.length <= 2048 ? url : '';
}

export function normalizeCoverTitle(value: string): string {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}

/** Selects an exact title first, then a prefix match, then the top result. */
export function selectSteamGridGame(games: SteamGridGame[], query: string): SteamGridGame | null {
  const target = normalizeCoverTitle(query);
  if (!target) return null;
  const available = games.filter((game) => game.id > 0 && normalizeCoverTitle(game.name));
  const exact = available.find((game) => normalizeCoverTitle(game.name) === target);
  if (exact) return exact;
  return (
    available.find((game) => normalizeCoverTitle(game.name).startsWith(target)) ??
    available[0] ??
    null
  );
}

export function parseSteamGridGames(payload: unknown): SteamGridGame[] {
  const body = record(payload);
  const data = body?.data;
  if (!Array.isArray(data)) return [];
  const games: SteamGridGame[] = [];
  for (const entry of data) {
    const item = record(entry);
    const id = integer(item?.id);
    const name = text(item?.name);
    if (id === null || !name) continue;
    const game: SteamGridGame = { id, name };
    if (typeof item?.verified === 'boolean') game.verified = item.verified;
    if (Array.isArray(item?.types)) {
      game.types = item.types.filter((type): type is string => typeof type === 'string').slice(0, 8);
    }
    const release = item?.release_date;
    if (typeof release === 'number' && Number.isFinite(release)) game.release_date = release;
    games.push(game);
  }
  return games;
}

export function parseSteamGridAssets(payload: unknown): SteamGridAsset[] {
  const body = record(payload);
  // The host proxy returns a flat `{ success, asset_type, assets }` payload.
  // The nested `data.assets` shape is kept as a fallback for upstream responses.
  const nested = record(body?.data);
  const assets = Array.isArray(body?.assets) ? body.assets : nested?.assets;
  if (!Array.isArray(assets)) return [];
  const parsed: SteamGridAsset[] = [];
  for (const entry of assets) {
    const item = record(entry);
    const id = integer(item?.id);
    const url = cdnUrl(item?.url);
    if (id === null || !url) continue;
    const asset: SteamGridAsset = { id, url };
    const thumb = cdnUrl(item?.thumb);
    if (thumb) asset.thumb = thumb;
    for (const key of ['width', 'height'] as const) {
      const value = item?.[key];
      if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) {
        asset[key] = value;
      }
    }
    for (const key of ['style', 'mime'] as const) {
      const value = text(item?.[key]);
      if (value) asset[key] = value;
    }
    for (const key of ['nsfw', 'humor'] as const) {
      if (typeof item?.[key] === 'boolean') asset[key] = item[key];
    }
    parsed.push(asset);
  }
  return parsed;
}

/**
 * Turns accepted assets into cover candidates. The result is capped and
 * de-duplicated; the thumbnail is used for preview and the original URL for
 * the local save, so the host never needs to hotlink the CDN afterwards.
 */
export function steamGridCandidates(
  game: SteamGridGame,
  assets: SteamGridAsset[],
): SteamGridCandidate[] {
  const seen = new Set<number>();
  const candidates: SteamGridCandidate[] = [];
  for (const asset of assets) {
    if (candidates.length >= MAX_CANDIDATES) break;
    if (asset.id <= 0 || seen.has(asset.id) || asset.nsfw || asset.humor) continue;
    if (!asset.url.startsWith('https://cdn2.steamgriddb.com/')) continue;
    seen.add(asset.id);
    candidates.push({
      name: game.name,
      key: `sgdb_${asset.id}`,
      url: asset.thumb ?? asset.url,
      saveUrl: asset.url,
      aspect: 'wide',
      source: 'steamgriddb',
    });
  }
  return candidates;
}
