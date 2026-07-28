/**
 * Cadence ramp.
 *
 * The standard way to raise cadence is to add a few steps per minute to what
 * you already run at and hold it, rather than jumping straight to a target
 * number: overstriding is a habit, and the leg turnover has to be trained.
 * A ramp starts at the cadence you are comfortable at and walks it up to the
 * target across the run, one step per minute at a time, slowly enough that you
 * do not notice a change happening.
 *
 * Pure maths, so the curve can be tested without running a stopwatch.
 */

import { clampBpm } from './beat-grid.js';

export const MIN_RAMP_MINUTES = 1;
export const MAX_RAMP_MINUTES = 60;

export function clampMinutes(value) {
  const rounded = Math.round(Number(value));
  if (!Number.isFinite(rounded)) return MIN_RAMP_MINUTES;
  return Math.min(MAX_RAMP_MINUTES, Math.max(MIN_RAMP_MINUTES, rounded));
}

/**
 * Cadence at a point in the ramp, rounded to a whole step per minute.
 *
 * @param {number} from starting cadence
 * @param {number} to target cadence
 * @param {number} minutes ramp length
 * @param {number} elapsedSeconds seconds since the ramp began
 */
export function rampBpmAt(from, to, minutes, elapsedSeconds) {
  const start = clampBpm(from);
  const target = clampBpm(to);
  const span = clampMinutes(minutes) * 60;
  if (elapsedSeconds <= 0) return start;
  if (elapsedSeconds >= span) return target;
  const progress = elapsedSeconds / span;
  return clampBpm(start + (target - start) * progress);
}

/** Fraction of the ramp completed, 0 to 1, for the progress readout. */
export function rampProgress(minutes, elapsedSeconds) {
  const span = clampMinutes(minutes) * 60;
  if (elapsedSeconds <= 0) return 0;
  return Math.min(1, elapsedSeconds / span);
}

export function rampDone(minutes, elapsedSeconds) {
  return rampProgress(minutes, elapsedSeconds) >= 1;
}
