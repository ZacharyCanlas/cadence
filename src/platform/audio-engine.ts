/**
 * The metronome engine.
 *
 * Timing model: every beat is placed on the audio hardware clock ahead of
 * time, at `anchor + index * (60 / bpm)`. The pump only tops that queue up, so
 * a late or throttled pump costs nothing as long as it fires before the queue
 * drains.
 *
 * That is the whole trick behind surviving the screen going off. While the app
 * is in front the queue runs a quarter of a second ahead, which keeps tempo
 * changes feeling instant. The moment the page is hidden the queue extends to
 * several seconds, so a pump throttled to one tick per second still arrives
 * with seconds of audio already committed to the hardware. Paired with the
 * keep-alive media element in `keep-alive.ts`, which stops the browser
 * suspending the page at all, the beat carries on through an app switch or a
 * screen lock.
 *
 * Everything queued is revocable. Each beat keeps a handle on the nodes and
 * timers it created, so a tempo change, a new sound or a stop can unwind the
 * seconds already committed instead of waiting for them to drain.
 */

import { beatIndexAt, beatPeriod, clampBpm, collectBeats } from '../core/beat-grid.ts';
import {
  capabilities,
  createAudioContext,
  setAudioSession,
  SESSION_BACKGROUND,
  SESSION_MIX,
} from './platform.ts';
import { createPump } from './pump.ts';
import { DEFAULT_VOICE, findVoice, type Voice } from '../core/sounds.ts';

/** Seconds of beats queued while the app is in front. Short, so tempo changes bite quickly. */
const HORIZON_FOREGROUND = 0.25;
/** Seconds queued once the app is hidden. Long enough to ride out a throttled pump. */
const HORIZON_HIDDEN = 6;
/** How often the pump tops the queue up. */
const PUMP_INTERVAL_MS = 25;
/** Beats missed by more than this are dropped rather than fired in a burst. */
const CATCHUP_TOLERANCE = 0.05;
/** Lead-in before the first beat, so the very first hit is never clipped. */
const START_LEAD = 0.08;
/** Nothing closer to the present than this can be reliably re-planned. */
const CHANGE_LEAD = 0.05;
/** Haptics run on timers, so they are only queued this far ahead. */
const VIBRATE_HORIZON = 1;
const VIBRATE_MS = 45;
const VIBRATE_ACCENT_MS = 90;

/** A beat the engine has committed to the audio clock, as announced to listeners. */
export interface EngineBeat {
  index: number;
  time: number;
  count: number;
  accent: boolean;
}

/**
 * A committed beat and the handles needed to revoke it: the per-beat gain
 * node, and the haptic timer if one is armed yet.
 */
interface QueuedBeat {
  time: number;
  count: number;
  accent: boolean;
  channel: GainNode | null;
  vibeTimer: ReturnType<typeof setTimeout> | null;
}

export interface MetronomeHandlers {
  onBeats?: (beats: EngineBeat[]) => void;
  /** Beats from this count on never sounded. */
  onRevoke?: (fromCount: number) => void;
  onStateChange?: () => void;
}

export interface Metronome {
  readonly running: boolean;
  readonly bpm: number;
  readonly contextState: string;
  readonly usingWorkerPump: boolean;
  /** The audio clock, which is what every scheduled beat time is measured against. */
  readonly time: number;
  /** Seconds since start, from the audio clock rather than wall time. */
  readonly elapsed: number;
  /** Must be called from a user gesture: the first audio unlock depends on it. */
  start(): void;
  stop(): void;
  setBpm(value: unknown): number;
  /** @param value 0 to 1 */
  setVolume(value: number): void;
  setVoice(id: string): string;
  setAccentEvery(value: unknown): number;
  setClick(enabled: boolean): void;
  setVibrate(enabled: boolean): void;
  setBackgroundMode(enabled: boolean): void;
  setHidden(value: boolean): void;
  /** One beat now, for the audition button. */
  preview(options?: { accent?: boolean }): void;
  /** Called when the app comes back to the front, or after an interruption. */
  resume(): Promise<void>;
}

export function createMetronome({
  onBeats = () => {},
  onRevoke = () => {},
  onStateChange = () => {},
}: MetronomeHandlers = {}): Metronome {
  let context: AudioContext | null = null;
  let master: GainNode | null = null;

  let running = false;
  let bpm = 180;
  let volume = 0.7;
  let clickOn = true;
  let vibrateOn = false;
  let backgroundMode = true;
  let voice: Voice = findVoice(DEFAULT_VOICE);
  let accentEvery = 0;

  let anchor = 0;
  let nextIndex = 0;
  let hidden = false;
  let startedAt = 0;

  /**
   * Beats queued but not yet sounded, newest last. `count` is the running beat
   * number since start, which is what the accent pattern keys off; it has to
   * be tracked separately from the grid index because the grid re-anchors on a
   * tempo change while the accent phase must carry straight through.
   */
  let queued: QueuedBeat[] = [];
  let nextCount = 0;

  const pump = createPump(schedule);

  function now(): number {
    return context ? context.currentTime : 0;
  }

  function horizon(): number {
    return hidden ? HORIZON_HIDDEN : HORIZON_FOREGROUND;
  }

  function isAccent(count: number): boolean {
    return accentEvery > 0 && count % accentEvery === 0;
  }

  function ensureContext(): AudioContext {
    if (!context) {
      applySessionType();
      context = createAudioContext();
      master = context.createGain();
      master.gain.value = volume;
      master.connect(context.destination);
      context.onstatechange = onStateChange;
    }
    // iOS reports the non-standard 'interrupted' as well as 'suspended' when
    // another app takes the output, so resume on anything that is not running.
    if (context.state !== 'running') {
      context.resume().then(onStateChange, onStateChange);
    }
    return context;
  }

  function applySessionType(): void {
    setAudioSession(backgroundMode ? SESSION_BACKGROUND : SESSION_MIX);
  }

  /**
   * A gain node the voice writes into, so one beat can be silenced on its own
   * without unpicking whatever graph the voice built underneath.
   */
  function playVoice(ctx: AudioContext, time: number, accent: boolean): GainNode {
    const channel = ctx.createGain();
    channel.gain.value = 1;
    channel.connect(master as GainNode);
    voice.play(ctx, channel, time, { accent });
    return channel;
  }

  function queueVibration(time: number, accent: boolean): ReturnType<typeof setTimeout> | null {
    const delay = (time - now()) * 1000;
    if (delay > VIBRATE_HORIZON * 1000) return null; // out of range; topUpVibrations retries
    return setTimeout(
      () => {
        if (running && vibrateOn) navigator.vibrate(accent ? VIBRATE_ACCENT_MS : VIBRATE_MS);
      },
      Math.max(0, delay),
    );
  }

  function releaseBeat(beat: QueuedBeat): void {
    if (beat.channel && master) {
      // Ramp to silence over a couple of milliseconds instead of cutting the
      // gain dead, which would put a click on the output.
      try {
        beat.channel.gain.setValueAtTime(beat.channel.gain.value, now());
        beat.channel.gain.linearRampToValueAtTime(0, now() + 0.002);
        beat.channel.disconnect(master);
      } catch {
        // Already detached; nothing left to unwind.
      }
    }
    if (beat.vibeTimer) clearTimeout(beat.vibeTimer);
  }

  /** Forget beats that have already sounded. */
  function prune(): void {
    const cutoff = now();
    queued = queued.filter((beat) => beat.time > cutoff);
  }

  /**
   * Revoke everything queued at or after `from` so it can be re-planned, and
   * hand the beat numbering back to where it was, so the accent pattern does
   * not shift when the tempo does.
   *
   * The revoked numbers are announced, because `schedule` will shortly hand out
   * the same ones again for the re-planned beats. A listener holding beats it
   * has not yet drawn or counted has no other way to tell the second beat 41
   * from the first, and would treat both as having sounded. That matters most
   * while hidden, where six seconds of beats are thrown away by every step of a
   * cadence ramp.
   */
  function revokeFrom(from: number): void {
    const kept: QueuedBeat[] = [];
    let earliestRevoked: number | null = null;
    for (const beat of queued) {
      if (beat.time < from) {
        kept.push(beat);
        continue;
      }
      releaseBeat(beat);
      if (earliestRevoked === null || beat.count < earliestRevoked) earliestRevoked = beat.count;
    }
    queued = kept;
    if (earliestRevoked !== null) {
      nextCount = earliestRevoked;
      onRevoke(earliestRevoked);
    }
  }

  /**
   * Arm the haptics that were out of range when their beat was queued.
   *
   * Sound goes onto the audio clock seconds ahead, but a vibration is a plain
   * timer and cannot usefully be set that far out, so anything past the horizon
   * is queued with no timer at all. Once the page is hidden the whole lookahead
   * sits past that horizon, which would leave every beat silent on the motor.
   * Each pass therefore re-checks what is queued and arms whatever has since
   * come into range.
   */
  function topUpVibrations(): void {
    if (!vibrateOn || !capabilities.vibrate) return;
    for (const beat of queued) {
      if (beat.vibeTimer === null) beat.vibeTimer = queueVibration(beat.time, beat.accent);
    }
  }

  function schedule(): void {
    if (!running || !context) return;
    const ctx = context;
    prune();

    const at = ctx.currentTime;
    const { beats, nextIndex: advanced } = collectBeats(
      anchor,
      bpm,
      nextIndex,
      at,
      horizon(),
      CATCHUP_TOLERANCE,
    );
    nextIndex = advanced;

    if (beats.length > 0) {
      const sounded: EngineBeat[] = [];
      for (const beat of beats) {
        const count = nextCount;
        nextCount += 1;
        const accent = isAccent(count);
        const entry: QueuedBeat = { time: beat.time, count, accent, channel: null, vibeTimer: null };
        if (clickOn) entry.channel = playVoice(ctx, beat.time, accent);
        if (vibrateOn && capabilities.vibrate) entry.vibeTimer = queueVibration(beat.time, accent);
        queued.push(entry);
        sounded.push({ index: beat.index, time: beat.time, count, accent });
      }
      onBeats(sounded);
    }

    // Runs on every pass, including the passes that queue nothing new, because
    // a full queue is exactly the state a hidden page spends its time in.
    topUpVibrations();
  }

  /** Re-plan from `at` with the current settings, keeping the beat phase intact. */
  function replanFrom(at: number): void {
    revokeFrom(at);
    nextIndex = beatIndexAt(anchor, bpm, at);
    schedule();
  }

  /**
   * Re-plan at a new tempo without a phase jump: the last beat that is still
   * committed stays where it is and the new spacing runs on from there.
   *
   * That beat is not always the last one to have sounded. Anything inside the
   * change lead is too close to the present to re-plan, so it survives the
   * revoke and will still be heard. Anchoring on the last beat in the past
   * instead would measure the new spacing from the wrong place, and a tempo
   * drop -- where the new period is longer -- could then put the first new beat
   * a few milliseconds after one of those survivors. Two clicks that close read
   * as a stumble rather than as a tempo change.
   */
  function rebuildGrid(ctx: AudioContext, nextBpm: number): void {
    const at = ctx.currentTime;
    const from = at + CHANGE_LEAD;
    revokeFrom(from);

    const elapsed = Math.max(0, Math.floor((at - anchor) / beatPeriod(bpm)));
    let lastBeat = anchor + elapsed * beatPeriod(bpm);
    for (const beat of queued) {
      if (beat.time > lastBeat) lastBeat = beat.time;
    }

    anchor = lastBeat;
    bpm = nextBpm;
    // Index 0 is the anchor, which is the beat still committed, so the run
    // resumes at 1 at the earliest. Counting from `at` rather than from the
    // change lead matters on a tempo rise: a slot landing inside the lead is a
    // beat that should still be heard, and rounding past it drops one, leaving
    // a gap of two periods where the ear expects one. Nothing kept can collide
    // with it, because everything kept is at or before the anchor.
    nextIndex = Math.max(1, beatIndexAt(anchor, bpm, at));
    schedule();
  }

  return {
    get running() {
      return running;
    },
    get bpm() {
      return bpm;
    },
    get contextState() {
      return context ? context.state : 'idle';
    },
    get usingWorkerPump() {
      return pump.usingWorker;
    },
    get time() {
      return now();
    },
    get elapsed() {
      return running && context ? Math.max(0, context.currentTime - startedAt) : 0;
    },

    start() {
      if (running) return;
      const ctx = ensureContext();
      running = true;
      anchor = ctx.currentTime + START_LEAD;
      startedAt = anchor;
      nextIndex = 0;
      nextCount = 0;
      queued = [];
      schedule();
      pump.start(PUMP_INTERVAL_MS);
      onStateChange();
    },

    stop() {
      if (!running) return;
      running = false;
      pump.stop();
      revokeFrom(0);
      if (vibrateOn && capabilities.vibrate) navigator.vibrate(0);
      onStateChange();
    },

    setBpm(value: unknown) {
      const next = clampBpm(value);
      if (next === bpm) return bpm;
      if (running && context) rebuildGrid(context, next);
      else bpm = next;
      return bpm;
    },

    setVolume(value: number) {
      volume = Math.min(1, Math.max(0, value));
      if (master) master.gain.value = volume;
    },

    setVoice(id: string) {
      voice = findVoice(id);
      // Re-plan so the new sound is heard on the next beat rather than after
      // the seconds already queued have drained.
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
      return voice.id;
    },

    setAccentEvery(value: unknown) {
      accentEvery = Math.max(0, Math.round(Number(value)) || 0);
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
      return accentEvery;
    },

    setClick(enabled: boolean) {
      clickOn = enabled;
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
    },

    setVibrate(enabled: boolean) {
      const wasOn = vibrateOn;
      vibrateOn = enabled;
      // Only cancel a pattern that could actually be running. Calling vibrate
      // before the page has been tapped is refused and logs an error.
      if (wasOn && !enabled && capabilities.vibrate) navigator.vibrate(0);
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
    },

    /**
     * Background mode decides which audio session Safari hands us: exclusive
     * output that survives a screen lock, or shared output that lets your music
     * carry on. See `platform.ts` for why it cannot be both.
     */
    setBackgroundMode(enabled: boolean) {
      backgroundMode = enabled;
      applySessionType();
    },

    /** The longer lookahead while hidden is what carries the beat through a lock. */
    setHidden(value: boolean) {
      if (hidden === value) return;
      hidden = value;
      if (running) schedule();
    },

    preview({ accent = false }: { accent?: boolean } = {}) {
      const ctx = ensureContext();
      playVoice(ctx, ctx.currentTime + 0.05, accent);
      onStateChange();
    },

    async resume() {
      if (!context) return;
      applySessionType();
      if (context.state !== 'running') {
        try {
          await context.resume();
        } catch {
          // Surfaced through contextState; the UI explains the retry.
        }
      }
      if (running) schedule();
      onStateChange();
    },
  };
}
