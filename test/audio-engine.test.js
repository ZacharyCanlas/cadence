import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createHarness } from './helpers/audio-harness.js';

/** Beat times are floats off the audio clock; compare with a tolerance, not exactly. */
function assertClose(actual, expected, tolerance, message) {
  assert.ok(
    Math.abs(actual - expected) <= tolerance,
    `${message}: expected ${expected} +/- ${tolerance}, got ${actual}`,
  );
}

/** Beats are re-emitted when a change re-plans them, so a count can appear more than once. */
function uniqueCounts(beats) {
  return [...new Set(beats.map((beat) => beat.count))].sort((a, b) => a - b);
}

test('the first beat is placed ahead of the clock, so the opening hit is not clipped', async (t) => {
  const { engine, beats, context } = await createHarness(t);

  engine.setBpm(180);
  engine.start();

  assert.equal(beats.length, 1, 'only the beat inside the foreground horizon is queued');
  assert.ok(beats[0].time > context.currentTime, 'the first beat is in the future');
  assertClose(beats[0].time, 0.08, 1e-9, 'the lead-in is the full START_LEAD');
});

test('beats stay one period apart across a long run, rather than accumulating error', async (t) => {
  const { engine, gaps, advance } = await createHarness(t);

  engine.setBpm(180);
  engine.start();
  advance(20);

  const period = 60 / 180;
  const spacings = gaps();
  assert.ok(spacings.length > 55, `expected roughly 60 beats in 20s, got ${spacings.length + 1}`);
  for (const gap of spacings) {
    assertClose(gap, period, 1e-9, 'every gap is exactly one period');
  }
});

test('the accent falls on the running beat count, not on the grid index', async (t) => {
  const { engine, beats, advance } = await createHarness(t);

  engine.setBpm(180);
  engine.setAccentEvery(4);
  engine.start();
  advance(5);

  for (const beat of beats) {
    assert.equal(beat.accent, beat.count % 4 === 0, `beat ${beat.count} accented correctly`);
  }
  assert.ok(
    beats.some((beat) => beat.accent),
    'at least one accent landed',
  );
});

test('a tempo change carries the accent phase straight through', async (t) => {
  const { engine, beats, advance } = await createHarness(t);

  engine.setBpm(160);
  engine.setAccentEvery(4);
  engine.start();
  advance(4);

  const before = uniqueCounts(beats).at(-1);
  engine.setBpm(200);
  advance(4);

  const counts = uniqueCounts(beats);
  assert.deepEqual(
    counts,
    counts.map((_, i) => i),
    'the beat numbering runs unbroken from zero across the tempo change',
  );
  assert.ok(counts.at(-1) > before, 'the run continued after the change');
});

test('a tempo change takes effect without waiting for the queue to drain', async (t) => {
  const { engine, beats, advance } = await createHarness(t);

  engine.setBpm(120);
  engine.start();
  advance(3);

  engine.setBpm(200);
  advance(3);

  const last = beats.slice(-6);
  const spacings = last.slice(1).map((beat, i) => beat.time - last[i].time);
  for (const gap of spacings) {
    assertClose(gap, 60 / 200, 1e-9, 'the new tempo is already sounding');
  }
});

test('stopping unwinds what was already committed to the hardware', async (t) => {
  const { engine, beats, advance } = await createHarness(t);

  engine.setBpm(180);
  engine.start();
  advance(2);

  const queuedByStop = beats.length;
  engine.stop();
  advance(5);

  assert.equal(beats.length, queuedByStop, 'nothing sounds after stop');
  assert.equal(engine.running, false);
});

test('hiding the page queues seconds ahead, so a throttled pump still has audio', async (t) => {
  const { engine, beats, advance, context } = await createHarness(t);

  engine.setBpm(180);
  engine.start();
  advance(1);

  const foregroundLead = beats.at(-1).time - context.currentTime;
  assert.ok(foregroundLead < 0.5, `foreground lead stays short, got ${foregroundLead}`);

  engine.setHidden(true);
  const hiddenLead = beats.at(-1).time - context.currentTime;
  assert.ok(hiddenLead > 5, `hidden lead covers a throttled pump, got ${hiddenLead}`);
});

test('a stall skips the beats it missed instead of firing them in a burst', async (t) => {
  const { engine, beats, advance, context } = await createHarness(t);

  engine.setBpm(180);
  engine.start();
  advance(1);

  // The page freezes: the audio clock runs on while the pump never fires.
  const before = beats.length;
  context._ms += 10_000;
  advance(1);

  const missed = beats.length - before;
  assert.ok(missed < 10, `a 10s stall does not queue 30 catch-up beats, got ${missed}`);
  for (const beat of beats.slice(before)) {
    assert.ok(beat.time > 10, 'every beat after the stall is on the live grid');
  }
});

test('the beat keeps vibrating once the page is hidden', async (t) => {
  const { engine, advance, vibrations } = await createHarness(t);

  engine.setBpm(180);
  engine.setVibrate(true);
  engine.start();
  advance(1);

  // Hiding stretches the lookahead to several seconds, which is exactly the
  // case where a haptic queued only a second ahead would be missed.
  engine.setHidden(true);
  vibrations.length = 0;
  advance(5);

  assert.ok(
    vibrations.length >= 14,
    `180bpm for 5s hidden is ~15 buzzes, got ${vibrations.length}`,
  );
});

test('a re-planned beat replaces the one it revoked rather than counting twice', async (t) => {
  // Mirrors what main.js does with the beat feed: hold the scheduled beats,
  // retire them as the clock passes them, and count each one as a step.
  let pending = [];
  const stepped = [];

  const { engine, advance, context } = await createHarness(t, {
    onBeats: (batch) => pending.push(...batch),
    onRevoke: (fromCount) => {
      pending = pending.filter((beat) => beat.count < fromCount);
    },
  });

  /** The drain the animation loop performs each frame. */
  function step(seconds) {
    for (let done = 0; done < seconds * 10; done += 1) {
      advance(0.1);
      while (pending.length && pending[0].time <= context.currentTime) {
        stepped.push(pending.shift().count);
      }
    }
  }

  engine.setBpm(180);
  engine.start();
  // Hidden is the case that matters: the lookahead stretches to six seconds, so
  // a tempo change throws away far more committed beats than it does in front.
  engine.setHidden(true);
  step(2);

  for (const bpm of [185, 190, 195, 200]) {
    engine.setBpm(bpm);
    step(3);
  }

  assert.equal(
    new Set(stepped).size,
    stepped.length,
    `every step is counted once; ${stepped.length - new Set(stepped).size} were double-counted`,
  );
});

test('a tempo change never lands a beat on top of one already committed', async (t) => {
  // Beats inside the change lead are too close to the present to re-plan, so
  // they survive and will still be heard. A tempo drop lengthens the period,
  // which is the case that can put the first beat of the new grid a few
  // milliseconds after one of those survivors.
  let live = [];
  const { engine, advance } = await createHarness(t, {
    onBeats: (batch) => live.push(...batch),
    onRevoke: (fromCount) => {
      live = live.filter((beat) => beat.count < fromCount);
    },
  });

  const fastest = 220;
  engine.setBpm(fastest);
  engine.start();

  // Odd offsets so the changes land at a spread of phases within the beat.
  const drops = [
    [204, 1.04],
    [188, 1.03],
    [172, 1.07],
    [156, 1.02],
    [140, 1.05],
    [124, 1.06],
    [180, 1.01],
    [164, 1.04],
  ];
  for (const [bpm, wait] of drops) {
    advance(wait);
    engine.setBpm(bpm);
  }
  advance(2);

  live.sort((a, b) => a.time - b.time);
  const floor = 60 / fastest;
  for (let i = 1; i < live.length; i += 1) {
    const gap = live[i].time - live[i - 1].time;
    assert.ok(
      gap >= floor - 1e-9,
      `beats ${live[i - 1].count} and ${live[i].count} are ${gap.toFixed(4)}s apart, ` +
        `closer than the ${floor.toFixed(4)}s of the fastest tempo used`,
    );
  }
});
