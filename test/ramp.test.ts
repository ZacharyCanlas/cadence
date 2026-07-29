import assert from 'node:assert/strict';
import { test } from 'node:test';

import { clampMinutes, rampBpmAt, rampDone, rampProgress } from '../src/core/ramp.ts';

test('the ramp starts where you started', () => {
  assert.equal(rampBpmAt(168, 180, 10, 0), 168);
  assert.equal(rampBpmAt(168, 180, 10, -5), 168);
});

test('the ramp finishes on the target and stays there', () => {
  assert.equal(rampBpmAt(168, 180, 10, 600), 180);
  assert.equal(rampBpmAt(168, 180, 10, 6000), 180);
});

test('the ramp climbs evenly across its span', () => {
  assert.equal(rampBpmAt(160, 180, 10, 300), 170); // halfway
  assert.equal(rampBpmAt(160, 180, 10, 150), 165); // a quarter in
});

test('a ramp can go down as well as up', () => {
  assert.equal(rampBpmAt(185, 165, 10, 300), 175);
  assert.equal(rampBpmAt(185, 165, 10, 600), 165);
});

test('cadence is always a whole number of steps per minute', () => {
  const value = rampBpmAt(168, 181, 7, 123);
  assert.equal(value, Math.round(value));
});

test('progress runs from nothing to complete and then holds', () => {
  assert.equal(rampProgress(10, 0), 0);
  assert.equal(rampProgress(10, 300), 0.5);
  assert.equal(rampProgress(10, 600), 1);
  assert.equal(rampProgress(10, 900), 1);
  assert.equal(rampDone(10, 599), false);
  assert.equal(rampDone(10, 600), true);
});

test('a nonsense duration is clamped instead of dividing by zero', () => {
  assert.equal(clampMinutes(0), 1);
  assert.equal(clampMinutes(-4), 1);
  assert.equal(clampMinutes(500), 60);
  assert.equal(clampMinutes('abc'), 1);
  assert.ok(Number.isFinite(rampBpmAt(170, 180, 0, 30)));
});
