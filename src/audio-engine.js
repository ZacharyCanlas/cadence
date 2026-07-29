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
 * keep-alive media element in `keep-alive.js`, which stops the browser
 * suspending the page at all, the beat carries on through an app switch or a
 * screen lock.
 *
 * Everything queued is revocable. Each beat keeps a handle on the nodes and
 * timers it created, so a tempo change, a new sound or a stop can unwind the
 * seconds already committed instead of waiting for them to drain.
 */

import { beatIndexAt, beatPeriod, clampBpm, collectBeats } from './beat-grid.js';
import {
  capabilities,
  createAudioContext,
  setAudioSession,
  SESSION_BACKGROUND,
  SESSION_MIX,
} from './platform.js';
import { createPump } from './pump.js';
import { DEFAULT_VOICE, findVoice } from './sounds.js';

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

/**
 * @param {object} handlers
 * @param {(beats: {index: number, time: number, accent: boolean}[]) => void} handlers.onBeats
 * @param {() => void} handlers.onStateChange
 */
export function createMetronome({ onBeats = () => {}, onStateChange = () => {} } = {}) {
  let context = null;
  let master = null;

  let running = false;
  let bpm = 180;
  let volume = 0.7;
  let clickOn = true;
  let vibrateOn = false;
  let backgroundMode = true;
  let voice = findVoice(DEFAULT_VOICE);
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
  let queued = [];
  let nextCount = 0;

  const pump = createPump(schedule);

  function now() {
    return context ? context.currentTime : 0;
  }

  function horizon() {
    return hidden ? HORIZON_HIDDEN : HORIZON_FOREGROUND;
  }

  function isAccent(count) {
    return accentEvery > 0 && count % accentEvery === 0;
  }

  function ensureContext() {
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

  function applySessionType() {
    setAudioSession(backgroundMode ? SESSION_BACKGROUND : SESSION_MIX);
  }

  /**
   * A gain node the voice writes into, so one beat can be silenced on its own
   * without unpicking whatever graph the voice built underneath.
   */
  function playVoice(time, accent) {
    const channel = context.createGain();
    channel.gain.value = 1;
    channel.connect(master);
    voice.play(context, channel, time, { accent });
    return channel;
  }

  function queueVibration(time, accent) {
    const delay = (time - now()) * 1000;
    if (delay > VIBRATE_HORIZON * 1000) return null; // out of range; topUpVibrations retries
    return setTimeout(
      () => {
        if (running && vibrateOn) navigator.vibrate(accent ? VIBRATE_ACCENT_MS : VIBRATE_MS);
      },
      Math.max(0, delay),
    );
  }

  function releaseBeat(beat) {
    if (beat.channel) {
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
  function prune() {
    const cutoff = now();
    queued = queued.filter((beat) => beat.time > cutoff);
  }

  /**
   * Revoke everything queued at or after `from` so it can be re-planned, and
   * hand the beat numbering back to where it was, so the accent pattern does
   * not shift when the tempo does.
   */
  function revokeFrom(from) {
    const kept = [];
    let earliestRevoked = null;
    for (const beat of queued) {
      if (beat.time < from) {
        kept.push(beat);
        continue;
      }
      releaseBeat(beat);
      if (earliestRevoked === null || beat.count < earliestRevoked) earliestRevoked = beat.count;
    }
    queued = kept;
    if (earliestRevoked !== null) nextCount = earliestRevoked;
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
  function topUpVibrations() {
    if (!vibrateOn || !capabilities.vibrate) return;
    for (const beat of queued) {
      if (beat.vibeTimer === null) beat.vibeTimer = queueVibration(beat.time, beat.accent);
    }
  }

  function schedule() {
    if (!running || !context) return;
    prune();

    const at = context.currentTime;
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
      const sounded = [];
      for (const beat of beats) {
        const count = nextCount;
        nextCount += 1;
        const accent = isAccent(count);
        const entry = { time: beat.time, count, accent, channel: null, vibeTimer: null };
        if (clickOn) entry.channel = playVoice(beat.time, accent);
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
  function replanFrom(at) {
    revokeFrom(at);
    nextIndex = beatIndexAt(anchor, bpm, at);
    schedule();
  }

  /**
   * Re-plan at a new tempo without a phase jump: the beat that just sounded
   * stays where it was and the new spacing runs on from there.
   */
  function rebuildGrid(nextBpm) {
    const at = context.currentTime;
    const elapsed = Math.max(0, Math.floor((at - anchor) / beatPeriod(bpm)));
    const lastBeat = anchor + elapsed * beatPeriod(bpm);

    revokeFrom(at + CHANGE_LEAD);
    anchor = lastBeat;
    bpm = nextBpm;
    nextIndex = beatIndexAt(anchor, bpm, at + CHANGE_LEAD);
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
    /** The audio clock, which is what every scheduled beat time is measured against. */
    get time() {
      return now();
    },
    /** Seconds since start, from the audio clock rather than wall time. */
    get elapsed() {
      return running && context ? Math.max(0, context.currentTime - startedAt) : 0;
    },

    /** Must be called from a user gesture: the first audio unlock depends on it. */
    start() {
      if (running) return;
      ensureContext();
      running = true;
      anchor = context.currentTime + START_LEAD;
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

    setBpm(value) {
      const next = clampBpm(value);
      if (next === bpm) return bpm;
      if (running && context) rebuildGrid(next);
      else bpm = next;
      return bpm;
    },

    /** @param {number} value 0 to 1 */
    setVolume(value) {
      volume = Math.min(1, Math.max(0, value));
      if (master) master.gain.value = volume;
    },

    setVoice(id) {
      voice = findVoice(id);
      // Re-plan so the new sound is heard on the next beat rather than after
      // the seconds already queued have drained.
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
      return voice.id;
    },

    setAccentEvery(value) {
      accentEvery = Math.max(0, Math.round(Number(value)) || 0);
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
      return accentEvery;
    },

    setClick(enabled) {
      clickOn = enabled;
      if (running && context) replanFrom(context.currentTime + CHANGE_LEAD);
    },

    setVibrate(enabled) {
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
     * carry on. See `platform.js` for why it cannot be both.
     */
    setBackgroundMode(enabled) {
      backgroundMode = enabled;
      applySessionType();
    },

    /** The longer lookahead while hidden is what carries the beat through a lock. */
    setHidden(value) {
      if (hidden === value) return;
      hidden = value;
      if (running) schedule();
    },

    /** One beat now, for the audition button. */
    preview({ accent = false } = {}) {
      ensureContext();
      playVoice(context.currentTime + 0.05, accent);
      onStateChange();
    },

    /** Called when the app comes back to the front, or after an interruption. */
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
