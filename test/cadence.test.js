import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createCadence } from '../src/cadence.js';
import { load, memoryStorage, save } from '../src/settings.js';

/** One step of the session clock, as main.js drives it. */
function tick(cadence, settings, elapsedSeconds) {
  const target = cadence.rampTarget(settings.rampTo, settings.rampMinutes, elapsedSeconds);
  if (target !== cadence.current) cadence.sound(target);
  return cadence.current;
}

/** A whole run, ticked every half second the way `tickSession` is. */
function run(cadence, settings, seconds) {
  cadence.beginRun();
  for (let elapsed = 0; elapsed <= seconds; elapsed += 0.5) {
    tick(cadence, settings, elapsed);
  }
  return cadence.current;
}

test('a ramp moves what is sounding without touching what was chosen', () => {
  const cadence = createCadence(150);
  const settings = { rampTo: 180, rampMinutes: 10 };

  const ended = run(cadence, settings, 600);

  assert.equal(ended, 180, 'the ramp reached its target');
  assert.equal(cadence.current, 180, 'and that is what is sounding');
  assert.equal(cadence.chosen, 150, 'but the cadence you chose is untouched');
});

test('the ramp still climbs on the run after the one that finished it', () => {
  // The defect: every ramp step was saved as the chosen cadence, so a run that
  // climbed 150 to 180 left 180 saved. The next run then climbed from 180 to
  // 180 -- a flat line, no warning, and the cadence you actually run at gone.
  const storage = memoryStorage();
  save(storage, { ...load(storage), bpm: 150, rampOn: true, rampTo: 180, rampMinutes: 10 });

  // First run, ramped to completion. main.js persists `chosen`, never `current`.
  const first = load(storage);
  const firstRun = createCadence(first.bpm);
  assert.equal(run(firstRun, first, 600), 180, 'the first run climbed');
  save(storage, { ...first, bpm: firstRun.chosen });

  // Second session: reopen the app from storage.
  const second = load(storage);
  assert.equal(second.bpm, 150, 'the saved cadence is still the one you chose');

  const secondRun = createCadence(second.bpm);
  secondRun.beginRun();
  assert.equal(secondRun.current, 150, 'the second run starts where the first did');
  assert.equal(tick(secondRun, second, 300), 165, 'and is halfway up at half time');
  assert.equal(run(secondRun, second, 600), 180, 'and reaches the target again');
});

test('a second run in the same session starts from your cadence, not the last target', () => {
  const cadence = createCadence(150);
  const settings = { rampTo: 180, rampMinutes: 10 };

  run(cadence, settings, 600);
  assert.equal(cadence.current, 180, 'the first run finished at the target');

  // Straight into another run. Nothing in between is allowed to be doing the
  // resetting on this one's behalf.
  cadence.beginRun();
  assert.equal(cadence.current, 150, 'the second run opens at your cadence');
  assert.equal(cadence.rampFrom, 150, 'and climbs from there, not from 180');
});

test('a deliberate change is the one thing that moves the chosen cadence', () => {
  const cadence = createCadence(150);

  cadence.choose(168);

  assert.equal(cadence.chosen, 168, 'the slider, a preset or a tap is a new choice');
  assert.equal(cadence.current, 168, 'and takes effect immediately');
  assert.equal(cadence.rampFrom, 168, 'and is where a ramp would now climb from');
});

test('a change made mid-ramp climbs on from there, not from where the run began', () => {
  const cadence = createCadence(150);
  const settings = { rampTo: 190, rampMinutes: 10 };

  cadence.beginRun();
  tick(cadence, settings, 300);
  assert.equal(cadence.current, 170, 'halfway up');

  cadence.choose(160);
  assert.equal(cadence.rampFrom, 160, 'the ramp is rebased on the new choice');
  assert.equal(cadence.chosen, 160, 'and the new choice is what would be saved');
});

test('ending a run puts the display back to the cadence you chose', () => {
  const cadence = createCadence(150);
  const settings = { rampTo: 180, rampMinutes: 10 };

  run(cadence, settings, 600);
  assert.equal(cadence.current, 180);

  cadence.endRun();
  assert.equal(cadence.current, 150, 'the readout returns to your cadence');
  assert.equal(cadence.chosen, 150, 'which never moved');
});

test('turning the ramp on mid-run climbs from where you actually are', () => {
  const cadence = createCadence(150);

  cadence.beginRun();
  cadence.sound(162); // as if the ramp, or a nudge, had already moved things
  cadence.rebaseRamp();

  assert.equal(cadence.rampFrom, 162, 'the climb starts from the current cadence');
  assert.equal(cadence.chosen, 150, 'without rewriting what you chose');
});

test('a cadence out of range is clamped rather than trusted', () => {
  const cadence = createCadence(9999);
  assert.equal(cadence.chosen, 220);
  assert.equal(cadence.current, 220);

  cadence.choose(10);
  assert.equal(cadence.chosen, 100);

  cadence.sound(9999);
  assert.equal(cadence.current, 220, 'the ramp cannot push past the range either');
});
