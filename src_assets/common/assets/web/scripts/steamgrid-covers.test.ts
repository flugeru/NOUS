import assert from 'node:assert/strict';
import test from 'node:test';
import {
  normalizeCoverTitle,
  parseSteamGridAssets,
  parseSteamGridGames,
  selectSteamGridGame,
  steamGridCandidates,
} from '../utils/steamGridCovers.ts';

test('selects exact titles and tolerates punctuation-free queries', () => {
  const games = [
    { id: 1, name: 'Portal 2: The Final Hours' },
    { id: 2, name: 'Portal 2' },
    { id: 3, name: 'Portal' },
  ];
  assert.equal(selectSteamGridGame(games, ' portal   2 ')?.id, 2);
  assert.equal(selectSteamGridGame(games, 'PORTAL')?.id, 3);
  assert.equal(selectSteamGridGame(games, 'Portal 2: The Final')?.id, 1);
  assert.equal(selectSteamGridGame(games, ''), null);
});

test('parses only bounded, typed games from the public response', () => {
  const games = parseSteamGridGames({
    success: true,
    data: [
      { id: 9022, name: ' Portal ', verified: true, types: ['steam', 7], release_date: 1191999600 },
      { id: 'bad', name: 'Ignored' },
      { id: 3, name: '   ' },
    ],
    token: 'never-exported',
  });
  assert.deepEqual(games, [
    { id: 9022, name: 'Portal', verified: true, types: ['steam'], release_date: 1191999600 },
  ]);
});

test('parses only official CDN artwork and drops unsafe URLs', () => {
  const assets = parseSteamGridAssets({
    success: true,
    asset_type: 'grid',
    assets: [
      {
        id: 577308,
        url: 'https://cdn2.steamgriddb.com/grid/a.png',
        thumb: 'https://cdn2.steamgriddb.com/thumb/a.jpg',
        width: 920,
        height: 430,
        style: 'material',
        mime: 'image/png',
        nsfw: false,
        humor: false,
        notes: '<p>not exported</p>',
      },
      { id: 2, url: 'http://cdn2.steamgriddb.com/grid/b.png' },
      { id: 3, url: 'https://evil.example/grid/c.png' },
    ],
  });
  assert.equal(assets.length, 1);
  assert.equal(assets[0]?.url, 'https://cdn2.steamgriddb.com/grid/a.png');
  assert.equal('notes' in (assets[0] ?? {}), false);
});

test('accepts the nested upstream asset shape as a fallback', () => {
  const assets = parseSteamGridAssets({
    success: true,
    data: {
      assets: [{ id: 7, url: 'https://cdn2.steamgriddb.com/grid/nested.png' }],
    },
  });
  assert.equal(assets.length, 1);
  assert.equal(assets[0]?.url, 'https://cdn2.steamgriddb.com/grid/nested.png');
});

test('candidates keep the thumbnail for preview and the original for saving', () => {
  const candidates = steamGridCandidates(
    { id: 9022, name: 'Portal' },
    [
      {
        id: 577308,
        url: 'https://cdn2.steamgriddb.com/grid/a.png',
        thumb: 'https://cdn2.steamgriddb.com/thumb/a.jpg',
        width: 920,
        height: 430,
      },
      {
        id: 577308,
        url: 'https://cdn2.steamgriddb.com/grid/a.png',
      },
      {
        id: 9,
        url: 'https://cdn2.steamgriddb.com/grid/nsfw.png',
        nsfw: true,
      },
    ],
  );
  assert.equal(candidates.length, 1);
  assert.deepEqual(candidates[0], {
    name: 'Portal',
    key: 'sgdb_577308',
    url: 'https://cdn2.steamgriddb.com/thumb/a.jpg',
    saveUrl: 'https://cdn2.steamgriddb.com/grid/a.png',
    aspect: 'wide',
    source: 'steamgriddb',
  });
});

test('title normalization never changes meaning through compatibility forms', () => {
  assert.equal(normalizeCoverTitle('  Portal\u00a0 2 '), 'portal 2');
  assert.equal(normalizeCoverTitle('\uFF30ortal'), 'portal');
});
