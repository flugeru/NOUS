import assert from 'node:assert/strict';
import test from 'node:test';
import { libraryCategory } from '../utils/libraryCategory.ts';

test('library categories honor manual choice without guessing from translated titles', () => {
  assert.equal(libraryCategory({ name: 'Desktop', cmd: 'game.exe' }), 'games');
  assert.equal(libraryCategory({ name: 'Game', cmd: '' }), 'desktop');
  assert.equal(libraryCategory({ cmd: 'Discord.exe', 'library-category': 'desktop' }), 'desktop');
  assert.equal(libraryCategory({ 'playnite-fullscreen': true }), 'launchers');
  assert.equal(libraryCategory({ 'steam-id': '123' }), 'games');
  for (const launch of ['steam://open/bigpicture', 'setsid steam steam://open/bigpicture', 'open steam://open/bigpicture']) {
    assert.equal(libraryCategory({ detached: [launch] }), 'launchers');
    assert.equal(libraryCategory({ detached: [launch], 'library-category': 'games' }), 'games');
  }
  assert.equal(libraryCategory({ name: 'Steam Big Picture', cmd: 'game.exe' }), 'games');
  assert.equal(libraryCategory({ detached: ['game.exe'] }), 'games');
  assert.equal(libraryCategory({ 'library-category': 'invalid', cmd: 'game.exe' }), 'games');
  assert.equal(libraryCategory({ 'effective-library-category': 'launchers', cmd: 'game.exe' }), 'launchers');
  assert.equal(libraryCategory({ 'library-category': 'desktop', 'effective-library-category': 'games' }), 'desktop');
  assert.equal(libraryCategory({ 'effective-library-category': 'invalid', cmd: 'game.exe' }), 'games');
});
