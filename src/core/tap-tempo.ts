/**
 * Tap tempo: work out a cadence from the runner tapping along to their own
 * stride. Time is passed in rather than read, so the averaging rules can be
 * tested deterministically.
 */

import { clampBpm } from './beat-grid.ts';

/** Taps further apart than this start a fresh measurement. */
export const TAP_TIMEOUT_MS = 2500;
/** Intervals kept in the rolling average. Enough to settle, short enough to follow a change. */
export const TAP_WINDOW = 6;
/** Two taps give a jittery reading, so hold off until there are three. */
export const MIN_TAPS = 3;

/** `bpm` stays null until the reading settles. */
export type TapReading = {
  bpm: number | null;
  taps: number;
}

export type TapTempo = {
  /** @param now milliseconds, from any monotonic-ish clock */
  tap(now: number): TapReading;
  reset(): void;
  readonly taps: number;
}

export function createTapTempo({ window = TAP_WINDOW, timeoutMs = TAP_TIMEOUT_MS } = {}): TapTempo {
  let lastTap: number | null = null;
  let intervals: number[] = [];

  return {
    tap(now: number): TapReading {
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

    get taps(): number {
      return lastTap === null ? 0 : intervals.length + 1;
    },
  };
}
