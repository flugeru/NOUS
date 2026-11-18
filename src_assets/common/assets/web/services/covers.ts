import { apiGet, apiPost } from '@/api/client';
import type { CoverCandidate } from '@/components/app-edit/AppEditCoverModal.types';
import {
  parseSteamGridAssets,
  parseSteamGridGames,
  selectSteamGridGame,
  steamGridCandidates,
} from '@/utils/steamGridCovers';

const GAME_DATABASE_URL = 'https://raw.githubusercontent.com/LizardByte/GameDB/gh-pages';
const STEAMGRID_CACHE_LIMIT = 40;
const steamGridCache = new Map<string, CoverCandidate[]>();

interface GameDatabaseBucketEntry {
  name?: unknown;
}

interface GameDatabaseRecord {
  id?: unknown;
  name?: unknown;
  cover?: {
    url?: unknown;
  };
}

interface CoverUploadResponse extends Record<string, unknown> {
  path?: unknown;
}

interface PlayniteCoverResponse extends Record<string, unknown> {
  status?: unknown;
}

function searchBucket(name: string): string {
  const prefix = name
    .substring(0, Math.min(name.length, 2))
    .toLocaleLowerCase()
    .replace(/[^a-z\d]/g, '');
  return prefix || '@';
}

function coverCandidate(game: GameDatabaseRecord): CoverCandidate | null {
  const id = typeof game.id === 'number' || typeof game.id === 'string' ? String(game.id) : '';
  const name = typeof game.name === 'string' ? game.name : '';
  const thumbnail = typeof game.cover?.url === 'string' ? game.cover.url : '';
  const dotIndex = thumbnail.lastIndexOf('.');
  const slashIndex = thumbnail.lastIndexOf('/');
  if (!id || !name || dotIndex < 0 || slashIndex < 0) return null;

  const slug = thumbnail.substring(slashIndex + 1, dotIndex);
  if (!slug) return null;
  return {
    name,
    key: `igdb_${id}`,
    url: `https://images.igdb.com/igdb/image/upload/t_cover_big/${slug}.jpg`,
    saveUrl: `https://images.igdb.com/igdb/image/upload/t_cover_big_2x/${slug}.png`,
    aspect: 'portrait',
    source: 'igdb',
  };
}

async function searchGameDatabase(name: string): Promise<CoverCandidate[]> {
  const response = await fetch(`${GAME_DATABASE_URL}/buckets/${searchBucket(name)}.json`);
  if (!response.ok) throw new Error('cover-search-failed');

  const bucket = (await response.json()) as Record<string, GameDatabaseBucketEntry>;
  const normalizedSearch = name.replace(/\s+/g, '.').toLocaleLowerCase();
  const matchingIds = Object.entries(bucket ?? {})
    .filter(([, item]) =>
      typeof item?.name === 'string'
        ? item.name.replace(/\s+/g, '.').toLocaleLowerCase().startsWith(normalizedSearch)
        : false,
    )
    .map(([id]) => id);

  const games = await Promise.all(
    matchingIds.map(async (id): Promise<GameDatabaseRecord | null> => {
      try {
        const gameResponse = await fetch(
          `${GAME_DATABASE_URL}/games/${encodeURIComponent(id)}.json`,
        );
        return gameResponse.ok ? ((await gameResponse.json()) as GameDatabaseRecord) : null;
      } catch {
        return null;
      }
    }),
  );

  return games
    .map((game) => (game ? coverCandidate(game) : null))
    .filter((candidate): candidate is CoverCandidate => candidate !== null);
}

/**
 * Public SteamGridDB artwork, proxied by the host to avoid the missing CORS
 * headers. The host already bounds, validates, and caches the upstream calls;
 * this layer adds a small per-browser cache for repeated searches in one visit.
 */
async function searchSteamGridCovers(name: string): Promise<CoverCandidate[]> {
  const cacheKey = name.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  const cached = steamGridCache.get(cacheKey);
  if (cached) return cached;

  const search = await apiGet<unknown>(
    `/api/covers/steamgrid/search?term=${encodeURIComponent(name)}`,
  );
  const game = selectSteamGridGame(parseSteamGridGames(search), name);
  if (!game) return [];

  const response = await apiPost<unknown>('/api/covers/steamgrid/assets', {
    game_id: game.id,
    asset_type: 'grid',
    limit: 24,
  });
  const candidates = steamGridCandidates(game, parseSteamGridAssets(response));
  if (candidates.length) {
    if (steamGridCache.size >= STEAMGRID_CACHE_LIMIT) {
      const oldest = steamGridCache.keys().next().value;
      if (oldest !== undefined) steamGridCache.delete(oldest);
    }
    steamGridCache.set(cacheKey, candidates);
  }
  return candidates;
}

/**
 * Searches both sources without letting one outage hide the other. SteamGridDB
 * results come first because they provide horizontal artwork; GameDB remains
 * the equivalent fallback and the local image upload stays available.
 */
export async function searchCovers(name: string): Promise<CoverCandidate[]> {
  const trimmedName = name.trim();
  if (!trimmedName) return [];

  const [steamGrid, gameDatabase] = await Promise.allSettled([
    searchSteamGridCovers(trimmedName),
    searchGameDatabase(trimmedName),
  ]);

  const candidates: CoverCandidate[] = [];
  if (steamGrid.status === 'fulfilled') candidates.push(...steamGrid.value);
  if (gameDatabase.status === 'fulfilled') candidates.push(...gameDatabase.value);

  if (
    !candidates.length &&
    steamGrid.status === 'rejected' &&
    gameDatabase.status === 'rejected'
  ) {
    throw new Error('cover-search-failed');
  }
  return candidates;
}

export async function uploadCover(cover: CoverCandidate): Promise<string> {
  const response = await apiPost<CoverUploadResponse>('/api/covers/upload', {
    key: cover.key,
    url: cover.saveUrl,
  });
  if (typeof response.path !== 'string' || !response.path) throw new Error('cover-upload-failed');
  return response.path;
}

export async function updatePlayniteCover(
  playniteId: string,
  cover: CoverCandidate,
): Promise<void> {
  const response = await apiPost<PlayniteCoverResponse>('/api/playnite/cover', {
    playnite_id: playniteId,
    cover_key: cover.key,
  });
  if (response.status !== true) throw new Error('playnite-cover-update-failed');
}
