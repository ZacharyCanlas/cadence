/**
 * Tap tempo: work out a cadence from the runner tapping along to their own
 * stride. Time is passed in rather than read, so the averaging rules can be
 * tested deterministically.
 */

import { clampBpm } from './beat-grid.js';

/** Taps further apart than this start a fresh measurement. */
export const TAP_TIMEOUT_MS = 2500;
/** Intervals kept in the rolling average. Enough to settle, short enough to follow a change. */
export const TAP_WINDOW = 6;
/** Two taps give a jittery reading, so hold off until there are three. */
export const MIN_TAPS = 3;

export function createTapTempo({ window = TAP_WINDOW, timeoutMs = TAP_TIMEOUT_MS } = {}) {
  let lastTap = null;
  let intervals = [];

  return {
    /**
     * @param {number} now milliseconds, from any monotonic-ish clock
     * @returns {{bpm: number|null, taps: number}} bpm is null until the reading settles
     */
    tap(now) {
      if (lastTap !== null) {
        const gap = now - lastTap;
        if (gap > timeoutMs || gap <= 0) intervals = [];
        else {
          intervals.push(gap);
          if (intervals.length > window) intervals.shift();
        }
      }
      lastTap = now;

      if (intervals.length < MIN_TAPS - 1) return { bpm: null, taps: intervals.length + 1 };

      const mean = intervals.reduce((sum, gap) => sum + gap, 0) / intervals.length;
      return { bpm: clampBpm(60000 / mean), taps: intervals.length + 1 };
    },

    reset() {
      lastTap = null;
      intervals = [];
    },

    get taps() {
      return lastTap === null ? 0 : intervals.length + 1;
    },
  };
}
