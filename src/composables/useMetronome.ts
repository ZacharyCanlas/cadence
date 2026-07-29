/**
 * The bridge between the framework-free engine and Vue.
 *
 * The engine, the cadence split, and the platform wrappers are exactly the
 * modules the vanilla app used; none of them know Vue exists. This composable
 * owns the one instance of each, mirrors the state the templates need into
 * refs, and exposes the actions the controls call. Every invariant the old
 * composition root enforced -- engine first, then persist what it applied;
 * ramp output never saved; the keep-alive started inside the user gesture --
 * is enforced here and nowhere else.
 *
 * One instance for the whole app, created on first use. The beat flash is
 * deliberately not a ref: it is a stream of events consumed imperatively by
 * the ring, because a class binding cannot restart a CSS animation reliably.
 */

import { readonly, ref, type Ref } from 'vue';
import { createCadence } from '../core/cadence.ts';
import { createMetronome, type EngineBeat } from '../platform/audio-engine.ts';
import { createKeepAlive } from '../platform/keep-alive.ts';
import { createMediaSession } from '../platform/media-session.ts';
import { createWakeLock } from '../platform/wake-lock.ts';
import { capabilities } from '../platform/platform.ts';
import { useSettings } from './useSettings.ts';

/** Beats queued far enough ahead that the queue is worth draining eagerly. */
const PENDING_LIMIT = 256;

export interface MetronomeStore {
  readonly running: Readonly<Ref<boolean>>;
  readonly currentBpm: Readonly<Ref<number>>;
  readonly elapsed: Readonly<Ref<number>>;
  readonly stepCount: Readonly<Ref<number>>;
  readonly contextState: Readonly<Ref<string>>;
  readonly keepAlivePlaying: Readonly<Ref<boolean>>;
  readonly wakeLockHeld: Readonly<Ref<boolean>>;
  /** Spoken by the aria-live region in the hero. */
  readonly announcement: Readonly<Ref<string>>;
  readonly usingWorkerPump: boolean;
  readonly wakeLockSupported: boolean;
  setBpm(value: unknown, options?: { fromRamp?: boolean }): number;
  start(): Promise<void>;
  stop(): void;
  toggleRun(): void;
  chooseVoice(id: string): void;
  chooseAccent(every: number): void;
  setClick(value: boolean): void;
  setVibrate(value: boolean): void;
  /** @param value 0 to 100, as the volume slider reads */
  setVolume(value: number): void;
  setBackground(value: boolean): void;
  setRampOn(value: boolean): void;
  /** Beat flash events for the ring. Returns an unsubscribe. */
  onBeat(listener: (beat: EngineBeat) => void): () => void;
}

let instance: MetronomeStore | null = null;

function createInstance(): MetronomeStore {
  const { settings, update } = useSettings();
  const cadence = createCadence(settings.bpm);

  const running = ref(false);
  const currentBpm = ref(cadence.current);
  const elapsed = ref(0);
  const stepCount = ref(0);
  const contextState = ref('idle');
  const keepAlivePlaying = ref(false);
  const wakeLockHeld = ref(false);
  const announcement = ref('');

  /** Beats scheduled but not yet reached, drained by the animation loop. */
  let pendingBeats: EngineBeat[] = [];
  let frame: number | null = null;
  let clockTimer: ReturnType<typeof setInterval> | null = null;

  const beatListeners = new Set<(beat: EngineBeat) => void>();

  const engine = createMetronome({
    onBeats(beats) {
      pendingBeats.push(...beats);
      // The animation loop is what normally drains this, and the browser stops
      // running it while the page is hidden. On a long background run the queue
      // would otherwise grow for as long as the app is out of sight.
      if (pendingBeats.length > PENDING_LIMIT) drainPast();
    },
    // A tempo change throws away what was queued and hands the same beat numbers
    // out again. Dropping the originals keeps the step total honest; without
    // this, every step of a ramp counts its lookahead twice.
    onRevoke(fromCount) {
      pendingBeats = pendingBeats.filter((beat) => beat.count < fromCount);
    },
    onStateChange: syncStatus,
  });

  const keepAlive = createKeepAlive();
  const wakeLock = createWakeLock(syncStatus);
  const mediaSession = createMediaSession({
    onPlay: () => start(),
    onStop: () => stop(),
  });

  function syncStatus(): void {
    running.value = engine.running;
    contextState.value = engine.contextState;
    keepAlivePlaying.value = keepAlive.playing;
    wakeLockHeld.value = wakeLock.held;
  }

  // --- cadence --------------------------------------------------------------

  function setBpm(value: unknown, { fromRamp = false }: { fromRamp?: boolean } = {}): number {
    const applied = engine.setBpm(value);
    if (applied === cadence.current) return applied;
    // Only a deliberate change is saved. Persisting the ramp's own output would
    // overwrite the cadence you chose with the ramp's target, and the next run
    // would have nothing left to climb. See core/cadence.ts.
    if (fromRamp) {
      cadence.sound(applied);
    } else {
      cadence.choose(applied);
      update({ bpm: cadence.chosen });
    }
    currentBpm.value = cadence.current;
    if (engine.running) mediaSession.describe(applied);
    return applied;
  }

  // --- start and stop -------------------------------------------------------

  async function start(): Promise<void> {
    if (engine.running) return;

    cadence.beginRun();
    currentBpm.value = cadence.current;
    stepCount.value = 0;
    elapsed.value = 0;
    pendingBeats = [];

    // Ordering matters: the audio session type has to be set before the context
    // starts, and the keep-alive element has to be played inside the same user
    // gesture that opened it.
    engine.setBackgroundMode(settings.background);
    engine.start();
    if (settings.background) {
      await keepAlive.start();
      mediaSession.activate(cadence.current);
    }
    wakeLock.request();

    announcement.value = `Started at ${cadence.current} steps per minute`;
    startLoops();
    syncStatus();
  }

  function stop(): void {
    if (!engine.running) return;
    engine.stop();
    // Back to the cadence you chose, rather than sitting at wherever the ramp
    // finished, so starting again is not a surprise.
    cadence.endRun();
    currentBpm.value = cadence.current;
    keepAlive.stop();
    mediaSession.deactivate();
    wakeLock.release();
    stopLoops();

    announcement.value = 'Stopped';
    syncStatus();
  }

  function toggleRun(): void {
    if (engine.running) stop();
    else start();
  }

  // --- animation and session clock ------------------------------------------

  function startLoops(): void {
    if (frame === null) frame = requestAnimationFrame(pulse);
    if (clockTimer === null) clockTimer = setInterval(tickSession, 500);
  }

  function stopLoops(): void {
    if (frame !== null) cancelAnimationFrame(frame);
    if (clockTimer !== null) clearInterval(clockTimer);
    frame = null;
    clockTimer = null;
  }

  /**
   * Retire every beat that has now sounded and report the last one, which is
   * the one worth showing. Counting happens here rather than in the animation
   * loop so the step total stays right while the page is hidden.
   */
  function drainPast(): EngineBeat | null {
    const now = engine.time;
    let last: EngineBeat | null = null;
    while (pendingBeats.length && pendingBeats[0].time <= now) {
      last = pendingBeats.shift() as EngineBeat;
      stepCount.value += 1;
    }
    return last;
  }

  /** Flash the ring in time with the audio clock, not with a timer of its own. */
  function pulse(): void {
    if (!engine.running) {
      frame = null;
      return;
    }
    const flashed = drainPast();
    if (flashed && !document.hidden) {
      for (const listener of beatListeners) listener(flashed);
    }
    frame = requestAnimationFrame(pulse);
  }

  /** Session readout and the cadence ramp, both derived from elapsed time. */
  function tickSession(): void {
    if (!engine.running) return;
    elapsed.value = engine.elapsed;

    if (!settings.rampOn) return;
    // Derived from elapsed time rather than accumulated, so a throttled timer
    // in the background changes nothing about where the ramp ends up.
    const target = cadence.rampTarget(settings.rampTo, settings.rampMinutes, elapsed.value);
    if (target !== cadence.current) setBpm(target, { fromRamp: true });
  }

  // --- control actions ------------------------------------------------------

  function chooseVoice(id: string): void {
    update({ voice: engine.setVoice(id) });
    // Audition it, so choosing a sound tells you what you picked.
    if (!engine.running && settings.click) engine.preview();
  }

  function chooseAccent(every: number): void {
    update({ accentEvery: engine.setAccentEvery(every) });
    if (!engine.running && settings.click) engine.preview({ accent: every > 0 });
  }

  function setClick(value: boolean): void {
    update({ click: value });
    engine.setClick(value);
  }

  function setVibrate(value: boolean): void {
    update({ vibrate: value });
    engine.setVibrate(value);
  }

  function setVolume(value: number): void {
    update({ volume: value });
    engine.setVolume(value / 100);
  }

  function setBackground(value: boolean): void {
    update({ background: value });
    engine.setBackgroundMode(value);
    if (engine.running) {
      if (value) {
        keepAlive.start().then(() => mediaSession.activate(cadence.current));
      } else {
        keepAlive.stop();
        mediaSession.deactivate();
      }
    }
    syncStatus();
  }

  function setRampOn(value: boolean): void {
    update({ rampOn: value });
    if (value) cadence.rebaseRamp();
  }

  function onBeat(listener: (beat: EngineBeat) => void): () => void {
    beatListeners.add(listener);
    return () => beatListeners.delete(listener);
  }

  // --- lifecycle ------------------------------------------------------------

  document.addEventListener('visibilitychange', () => {
    engine.setHidden(document.hidden);
    if (document.hidden) return;
    engine.resume();
    // The system drops the wake lock whenever the page is hidden, so it has to
    // be taken again on the way back.
    if (engine.running) wakeLock.request();
    if (engine.running && frame === null) startLoops();
    syncStatus();
  });

  // iOS can hand the page back from the cache without a visibility change.
  window.addEventListener('pageshow', () => {
    if (engine.running) engine.resume();
  });

  // --- first configuration --------------------------------------------------

  engine.setBpm(cadence.current);
  engine.setVolume(settings.volume / 100);
  engine.setClick(settings.click);
  engine.setVibrate(settings.vibrate && capabilities.vibrate);
  engine.setVoice(settings.voice);
  engine.setAccentEvery(settings.accentEvery);
  engine.setBackgroundMode(settings.background);
  engine.setHidden(document.hidden);
  syncStatus();

  return {
    running: readonly(running),
    currentBpm: readonly(currentBpm),
    elapsed: readonly(elapsed),
    stepCount: readonly(stepCount),
    contextState: readonly(contextState),
    keepAlivePlaying: readonly(keepAlivePlaying),
    wakeLockHeld: readonly(wakeLockHeld),
    announcement: readonly(announcement),
    usingWorkerPump: engine.usingWorkerPump,
    wakeLockSupported: wakeLock.supported,

    setBpm,
    start,
    stop,
    toggleRun,
    chooseVoice,
    chooseAccent,
    setClick,
    setVibrate,
    setVolume,
    setBackground,
    setRampOn,
    onBeat,
  };
}

export function useMetronome(): MetronomeStore {
  if (!instance) instance = createInstance();
  return instance;
}
