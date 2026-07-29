/**
 * The cadence you chose, and the cadence currently sounding.
 *
 * These are two different numbers and the app used to keep only one. A ramp
 * moves the cadence a step at a time, and every step was written back as your
 * setting, so a run that climbed from 150 to 180 saved 180. The next run then
 * started at 180 and climbed to 180: a flat line, no warning, and the cadence
 * you had actually chosen gone for good. A ramp worked exactly once, and left
 * you starting every later run at the overstriding number the feature exists to
 * train you out of.
 *
 * So: `chosen` is yours, changed only when you move the slider, tap a preset or
 * tap out a tempo, and it is the only part worth persisting. `current` is what
 * the metronome is doing this second, which the ramp is free to move and which
 * nobody should remember afterwards. Outside a run the two are the same.
 *
 * No DOM and no storage, so the rule that a ramp must not overwrite its own
 * starting point can be tested rather than argued about.
 */

import { clampBpm } from './beat-grid.js';
import { rampBpmAt } from './ramp.js';

/**
 * @param {number} startingBpm the cadence restored from settings
 */
export function createCadence(startingBpm) {
  let chosen = clampBpm(startingBpm);
  let current = chosen;
  /** Where the running ramp climbs from. */
  let rampFrom = chosen;

  return {
    /** The cadence you picked. What gets saved. */
    get chosen() {
      return chosen;
    },
    /** The cadence sounding right now. Never saved. */
    get current() {
      return current;
    },
    get rampFrom() {
      return rampFrom;
    },

    /**
     * A deliberate change: the slider, a preset, a tap. This is a new choice,
     * so it moves everything, including where a ramp would climb from.
     */
    choose(value) {
      chosen = clampBpm(value);
      current = chosen;
      rampFrom = chosen;
      return chosen;
    },

    /**
     * The ramp moving on its own. Moves what is sounding and nothing else,
     * which is the whole point of the split.
     */
    sound(value) {
      current = clampBpm(value);
      return current;
    },

    /** A run begins: back to the cadence you chose, and climb from there. */
    beginRun() {
      current = chosen;
      rampFrom = chosen;
      return current;
    },

    /**
     * A run ends. The display returns to your cadence rather than sitting at
     * wherever the ramp finished, so starting again is not a surprise.
     */
    endRun() {
      current = chosen;
      return current;
    },

    /** The ramp switched on mid-run: climb from where you actually are. */
    rebaseRamp() {
      rampFrom = current;
      return rampFrom;
    },

    /**
     * Where the ramp should have reached by now. Derived from elapsed time
     * rather than accumulated, so a throttled timer in the background changes
     * nothing about where it ends up.
     */
    rampTarget(target, minutes, elapsedSeconds) {
      return rampBpmAt(rampFrom, target, minutes, elapsedSeconds);
    },
  };
}
