import assert from 'node:assert/strict';
import { test } from 'node:test';

import { DEFAULTS, load, memoryStorage, normalize, save } from '../src/settings.js';

test('defaults survive an empty store', () => {
  assert.deepEqual(load(memoryStorage()), DEFAULTS);
});

test('a saved round trip returns what went in', () => {
  const storage = memoryStorage();
  const settings = {
    bpm: 165,
    volume: 40,
    click: false,
    vibrate: true,
    background: false,
    voice: 'woodblock',
    accentEvery: 3,
    rampOn: true,
    rampTo: 185,
    rampMinutes: 20,
  };
  assert.equal(save(storage, settings), true);
  assert.deepEqual(load(storage), settings);
});

test('out-of-range values are clamped rather than trusted', () => {
  const settings = normalize({ bpm: 400, volume: -20, rampMinutes: 999 });
  assert.equal(settings.bpm, 220);
  assert.equal(settings.volume, 0);
  assert.equal(settings.rampMinutes, 60);
});

test('an unknown sound falls back rather than leaving the app silent', () => {
  assert.equal(normalize({ voice: 'trombone' }).voice, DEFAULTS.voice);
  assert.equal(normalize({ voice: 'kit' }).voice, 'kit');
});

test('an accent pattern outside the offered set is treated as off', () => {
  assert.equal(normalize({ accentEvery: 5 }).accentEvery, 0);
  assert.equal(normalize({ accentEvery: 3 }).accentEvery, 3);
});

test('corrupt stored JSON degrades to defaults', () => {
  const storage = memoryStorage();
  storage.setItem('cadence.settings.v1', '{not json');
  assert.deepEqual(load(storage), DEFAULTS);
});

test('a stored shape from another version is coerced, not rejected wholesale', () => {
  const settings = normalize({ bpm: '175', click: 'yes', vibrate: null });
  assert.equal(settings.bpm, 175);
  assert.equal(settings.click, DEFAULTS.click);
  assert.equal(settings.vibrate, DEFAULTS.vibrate);
});

test('both output channels can never be off at once', () => {
  const settings = normalize({ click: false, vibrate: false });
  assert.equal(settings.click, true);
});

test('a storage that throws does not take the app down', () => {
  const hostile = {
    getItem() {
      throw new Error('storage disabled');
    },
    setItem() {
      throw new Error('storage disabled');
    },
    removeItem() {},
  };
  assert.deepEqual(load(hostile), DEFAULTS);
  assert.equal(save(hostile, DEFAULTS), false);
});
