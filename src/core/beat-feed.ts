/**
 * The listener's side of the beat protocol: hold what the engine has queued,
 * retire what has sounded, and keep an honest step count while revocations
 * re-issue beat numbers.
 *
 * This bookkeeping used to live inline in the composition root, where it was
 * checked by reading it (see docs/findings.md). It has no DOM and no audio in
 * it -- time is passed in -- so it lives here, where it can be tested.
 */

/** The slice of an engine beat the feed cares about. */
export type FeedBeat = {
  time: number;
  count: number;
  accent: boolean;
};

export type BeatFeed<T extends FeedBeat> = {
  /** Beats queued but not yet reached. */
  readonly pending: number;
  /** Beats that have actually sounded since the last reset. */
  readonly steps: number;
  enqueue(beats: readonly T[]): void;
  /**
   * A revocation: beats from this count on were unwound by the engine and
   * their numbers will be handed out again. Dropping them keeps the step
   * total honest; without this, every step of a ramp counts its lookahead
   * twice.
   */
  revokeFrom(fromCount: number): void;
  /**
   * Retire every beat that has sounded by `now` and report the last one,
   * which is the one worth showing.
   */
  drainPast(now: number): T | null;
  /** A new run: nothing pending, nothing counted. */
  reset(): void;
};

export function createBeatFeed<T extends FeedBeat>(): BeatFeed<T> {
  let pending: T[] = [];
  let steps = 0;

  return {
    get pending() {
      return pending.length;
    },
    get steps() {
      return steps;
    },

    enqueue(beats) {
      pending.push(...beats);
    },

    revokeFrom(fromCount) {
      pending = pending.filter((beat) => beat.count < fromCount);
    },

    drainPast(now) {
      let last: T | null = null;
      while (pending.length && pending[0].time <= now) {
        last = pending.shift() as T;
        steps += 1;
      }
      return last;
    },

    reset() {
      pending = [];
      steps = 0;
    },
  };
}
