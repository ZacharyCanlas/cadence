/**
 * Beat grid maths. Pure functions, no DOM and no audio, so the timing rules
 * can be unit tested on their own.
 *
 * Every beat is derived from an anchor time rather than accumulated:
 * `anchor + index * (60 / bpm)`. Accumulating a period would let float error
 * pile up over a long run; deriving from the anchor keeps every beat exact.
 */

/** Covers a walking warm-up through to fast strides, matching the range
 *  running metronomes generally offer. */
export const MIN_BPM = 100;
export const MAX_BPM = 220;
export const DEFAULT_BPM = 180;

/** Ceiling on beats returned per scheduling pass, so a bad clock reading or a
 *  huge horizon can never spin the scheduler into a long loop. */
export const MAX_BEATS_PER_PASS = 256;

export function clampBpm(value) {
  const rounded = Math.round(Number(value));
  if (!Number.isFinite(rounded)) return DEFAULT_BPM;
  return Math.min(MAX_BPM, Math.max(MIN_BPM, rounded));
}

/** Seconds between beats. */
export function beatPeriod(bpm) {
  return 60 / bpm;
}

/** Clock time of a given beat index. */
export function beatTime(anchor, bpm, index) {
  return anchor + index * beatPeriod(bpm);
}

/** Index of the earliest beat landing at or after `time`. */
export function beatIndexAt(anchor, bpm, time) {
  const raw = (time - anchor) / beatPeriod(bpm);
  // Nudge down by an epsilon so a beat sitting exactly on `time` counts as
  // "at or after" rather than being rounded past by float noise.
  const index = Math.ceil(raw - 1e-9);
  // Math.ceil of a small negative gives -0, which would leak into beat
  // numbering and accent arithmetic. Normalise it away at the source.
  return index === 0 ? 0 : index;
}

/**
 * Beats that fall inside the scheduling window, starting at `fromIndex`.
 *
 * If the caller was stalled (backgrounded tab, a long GC pause) so that
 * `fromIndex` is already more than `tolerance` in the past, the grid is
 * skipped forward to the next live slot. Without that, waking from a ten
 * second stall would fire thirty missed clicks at once.
 *
 * @returns {{beats: {index: number, time: number}[], nextIndex: number}}
 */
export function collectBeats(anchor, bpm, fromIndex, now, horizon, tolerance = 0.05) {
  const period = beatPeriod(bpm);
  let index = fromIndex;

  if (beatTime(anchor, bpm, index) < now - tolerance) {
    index = beatIndexAt(anchor, bpm, now - tolerance);
  }

  const beats = [];
  const until = now + horizon;
  while (beats.length < MAX_BEATS_PER_PASS) {
    const time = beatTime(anchor, bpm, index);
    if (time >= until) break;
    beats.push({ index, time });
    index += 1;
  }
  return { beats, nextIndex: index };
}

/**
 * Re-anchor the grid so the run continues from `atTime` with beat 0 there.
 * Used on a tempo change: the beat already playing stays where it is and the
 * new spacing applies cleanly from the next beat on.
 */
export function reanchor(atTime) {
  return { anchor: atTime, index: 0 };
}
