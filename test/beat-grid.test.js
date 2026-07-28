import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  beatIndexAt,
  beatPeriod,
  beatTime,
  clampBpm,
  collectBeats,
  MAX_BEATS_PER_PASS,
  MAX_BPM,
  MIN_BPM,
} from '../src/beat-grid.js';

test('clampBpm holds the tempo inside the runnable range', () => {
  assert.equal(clampBpm(180), 180);
  assert.equal(clampBpm(10), MIN_BPM);
  assert.equal(clampBpm(9999), MAX_BPM);
  assert.equal(clampBpm(179.6), 180);
});

test('clampBpm falls back to the default on junk input', () => {
  assert.equal(clampBpm('not a number'), 180);
  assert.equal(clampBpm(undefined), 180);
  assert.equal(clampBpm(NaN), 180);
});

test('beat times are derived from the anchor, so they never drift', () => {
  const anchor = 100;
  const bpm = 180;
  // An hour of beats at 180 bpm. Accumulating a period here would visibly drift.
  const index = 180 * 60;
  const expected = anchor + index * (60 / 180);
  assert.equal(beatTime(anchor, bpm, index), expected);
  assert.ok(Math.abs(beatTime(anchor, bpm, index) - (anchor + 3600)) < 1e-9);
});

test('beatPeriod matches the tempo', () => {
  assert.equal(beatPeriod(120), 0.5);
  assert.equal(beatPeriod(180), 1 / 3);
});

test('beatIndexAt returns the beat landing exactly on the boundary', () => {
  const anchor = 10;
  const bpm = 120; // half-second beats
  assert.equal(beatIndexAt(anchor, bpm, 10), 0);
  assert.equal(beatIndexAt(anchor, bpm, 10.5), 1);
  assert.equal(beatIndexAt(anchor, bpm, 10.4), 1);
  assert.equal(beatIndexAt(anchor, bpm, 12), 4);
});

test('collectBeats fills exactly the requested horizon', () => {
  const { beats, nextIndex } = collectBeats(0, 120, 0, 0, 1.0);
  assert.deepEqual(
    beats.map((b) => b.time),
    [0, 0.5],
  );
  assert.equal(nextIndex, 2);
});

test('collectBeats resumes where the previous pass stopped', () => {
  const first = collectBeats(0, 120, 0, 0, 1.0);
  const second = collectBeats(0, 120, first.nextIndex, 0.5, 1.0);
  assert.deepEqual(
    second.beats.map((b) => b.time),
    [1.0],
  );
});

test('a long stall skips missed beats instead of firing them in a burst', () => {
  // Ten seconds of 180 bpm is thirty beats. Waking up should schedule the next
  // live beat, not thirty at once.
  const { beats } = collectBeats(0, 180, 0, 10, 0.25);
  assert.ok(beats.length <= 1);
  assert.ok(beats.every((beat) => beat.time >= 10 - 0.05));
});

test('a beat only just missed is still played', () => {
  // Inside the tolerance window, so it should survive rather than be dropped.
  const { beats } = collectBeats(0, 120, 0, 0.02, 0.25);
  assert.equal(beats[0].time, 0);
});

test('the per-pass beat count is capped', () => {
  const { beats } = collectBeats(0, 200, 0, 0, 10_000);
  assert.equal(beats.length, MAX_BEATS_PER_PASS);
});

test('a hidden-page horizon queues several seconds of beats', () => {
  const { beats } = collectBeats(0, 180, 0, 0, 6);
  // Six seconds at 180 bpm is eighteen beats, which is what has to already be
  // committed to the hardware for a throttled pump to go unnoticed.
  assert.equal(beats.length, 18);
});

