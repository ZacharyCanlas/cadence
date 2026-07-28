import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createTapTempo, MIN_TAPS, TAP_TIMEOUT_MS } from '../src/tap-tempo.js';

/** Tap at a steady interval and return the last reading. */
function tapSteady(tempo, intervalMs, count, startAt = 1000) {
  let reading = { bpm: null, taps: 0 };
  for (let i = 0; i < count; i += 1) reading = tempo.tap(startAt + i * intervalMs);
  return reading;
}

test('no reading until enough taps have landed', () => {
  const tempo = createTapTempo();
  assert.equal(tempo.tap(1000).bpm, null);
  assert.equal(tempo.tap(1333).bpm, null);
  assert.ok(tapSteady(createTapTempo(), 333, MIN_TAPS).bpm !== null);
});

test('a steady tap reads back as the tempo tapped', () => {
  // 333.33ms between taps is 180 steps per minute.
  const reading = tapSteady(createTapTempo(), 1000 / 3, 6);
  assert.equal(reading.bpm, 180);
});

test('the reading is clamped into the runnable range', () => {
  // Frantic tapping should not produce an unusable number.
  assert.equal(tapSteady(createTapTempo(), 50, 6).bpm, 220);
  assert.equal(tapSteady(createTapTempo(), 2000, 6).bpm, 100);
});

test('a long pause starts a fresh measurement', () => {
  const tempo = createTapTempo();
  tapSteady(tempo, 500, 4); // settles at 120
  const afterPause = tempo.tap(1000 + 3 * 500 + TAP_TIMEOUT_MS + 1);
  assert.equal(afterPause.bpm, null, 'the stale intervals should have been dropped');
  assert.equal(afterPause.taps, 1);
});

test('only the most recent taps count, so a change is followed', () => {
  const tempo = createTapTempo({ window: 4 });
  let at = 1000;
  for (let i = 0; i < 6; i += 1) {
    tempo.tap(at);
    at += 500; // 120 spm
  }
  for (let i = 0; i < 6; i += 1) {
    tempo.tap(at);
    at += 1000 / 3; // 180 spm
  }
  assert.equal(tempo.tap(at).bpm, 180, 'the older slow taps should have aged out');
});

test('reset clears the run of taps', () => {
  const tempo = createTapTempo();
  tapSteady(tempo, 333, 5);
  tempo.reset();
  assert.equal(tempo.taps, 0);
  assert.equal(tempo.tap(9999).bpm, null);
});
