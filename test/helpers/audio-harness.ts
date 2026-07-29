/**
 * A fake Web Audio environment, so the scheduler can be unit tested.
 *
 * `audio-engine.ts` is the one module that is neither pure logic nor a thin
 * wrapper, and it is where a timing mistake would be least visible: the beat
 * does not throw, it just sits slightly wrong for forty minutes. Everything it
 * needs from the browser is small and mechanical, so it can be faked here
 * rather than driven through a real browser.
 *
 * Two clocks have to move together. `context.currentTime` is what every
 * scheduled beat time is measured against, and the pump and the vibration
 * timers run on `setInterval`/`setTimeout`. `advance()` steps both in lockstep
 * in small slices, so a timer never fires at a moment when the audio clock
 * disagrees about what time it is.
 *
 * The globals are installed once, at module load, because `platform.ts` reads
 * `window` and `navigator` when it is first evaluated and freezes the result.
 * A stable constructor is installed there and handed a fresh context per
 * harness, which keeps module caching from leaking one test into the next.
 */

import type { TestContext } from 'node:test';
import type { EngineBeat, Metronome, MetronomeHandlers } from '../../src/platform/audio-engine.ts';

/** Low enough to keep the noise buffer cheap to fill; nothing here cares about fidelity. */
const SAMPLE_RATE = 8000;
/** Clock slice for `advance`. Well under the 25ms pump interval, so no tick is skipped. */
const STEP_MS = 5;

type ParamEvent = [kind: string, value: number | null, time: number];

export class FakeParam {
  value: number;
  /** Every automation call, in order, as [kind, value, time]. */
  events: ParamEvent[];

  constructor(value = 0) {
    this.value = value;
    this.events = [];
  }

  setValueAtTime(value: number, time: number): this {
    this.events.push(['set', value, time]);
    this.value = value;
    return this;
  }

  linearRampToValueAtTime(value: number, time: number): this {
    this.events.push(['linear', value, time]);
    return this;
  }

  exponentialRampToValueAtTime(value: number, time: number): this {
    this.events.push(['exponential', value, time]);
    return this;
  }

  cancelScheduledValues(time: number): this {
    this.events.push(['cancel', null, time]);
    return this;
  }
}

export class FakeNode {
  kind: string;
  outputs: FakeNode[];
  startedAt: number | null;
  stoppedAt: number | null;
  gain?: FakeParam;
  frequency?: FakeParam;
  Q?: FakeParam;
  type?: string;
  buffer?: unknown;

  constructor(kind: string) {
    this.kind = kind;
    this.outputs = [];
    this.startedAt = null;
    this.stoppedAt = null;
  }

  /** Returns the target, which is what lets voices write `a.connect(b).connect(c)`. */
  connect(target: FakeNode): FakeNode {
    this.outputs.push(target);
    return target;
  }

  disconnect(target?: FakeNode): void {
    if (target) this.outputs = this.outputs.filter((output) => output !== target);
    else this.outputs = [];
  }

  start(time: number): void {
    this.startedAt = time;
  }

  stop(time: number): void {
    this.stoppedAt = time;
  }
}

export class FakeAudioContext {
  sampleRate: number;
  state: string;
  destination: FakeNode;
  onstatechange: (() => void) | null;
  /** Every node handed out, so a test can assert on what was built. */
  nodes: FakeNode[];
  resumeCount: number;
  _ms: number;

  constructor() {
    this.sampleRate = SAMPLE_RATE;
    this.state = 'running';
    this.destination = new FakeNode('destination');
    this.onstatechange = null;
    this.nodes = [];
    this.resumeCount = 0;
    this._ms = 0;
  }

  get currentTime(): number {
    return this._ms / 1000;
  }

  _track(node: FakeNode): FakeNode {
    this.nodes.push(node);
    return node;
  }

  createGain(): FakeNode {
    const node = new FakeNode('gain');
    node.gain = new FakeParam(1);
    return this._track(node);
  }

  createOscillator(): FakeNode {
    const node = new FakeNode('oscillator');
    node.type = 'sine';
    node.frequency = new FakeParam(440);
    return this._track(node);
  }

  createBiquadFilter(): FakeNode {
    const node = new FakeNode('filter');
    node.type = 'lowpass';
    node.frequency = new FakeParam(350);
    node.Q = new FakeParam(1);
    return this._track(node);
  }

  createBufferSource(): FakeNode {
    const node = new FakeNode('bufferSource');
    node.buffer = null;
    return this._track(node);
  }

  createBuffer(channels: number, length: number) {
    return { length, numberOfChannels: channels, getChannelData: () => new Float32Array(length) };
  }

  resume(): Promise<void> {
    this.resumeCount += 1;
    this.state = 'running';
    return Promise.resolve();
  }
}

/** A bare fake context, for testing voices without an engine around them. */
export function createFakeContext(): FakeAudioContext {
  return new FakeAudioContext();
}

export interface VibrationRecord {
  at: number;
  pattern: number;
}

/** The context the installed constructor hands out. Swapped per harness. */
let currentContext: FakeAudioContext | null = null;
/** Vibration calls, stamped with the audio time at which they landed. */
let currentVibrations: VibrationRecord[] | null = null;
/** Audio session types set through `platform.ts`, in order. */
let currentSessions: string[] | null = null;

class InstalledAudioContext {
  constructor() {
    if (!currentContext) throw new Error('No harness is active; call createHarness first');
    return currentContext;
  }
}

// Installed once, before anything imports `platform.ts`. The fakes are
// structural stand-ins, not real DOM types, so the installs go through `any`.
(globalThis as any).window = { AudioContext: InstalledAudioContext, webkitAudioContext: undefined };
Object.defineProperty(globalThis, 'navigator', {
  configurable: true,
  writable: true,
  value: {
    userAgent: 'harness',
    maxTouchPoints: 0,
    vibrate(pattern: number) {
      currentVibrations?.push({ at: currentContext ? currentContext.currentTime : 0, pattern });
      return true;
    },
    audioSession: {
      set type(value: string) {
        currentSessions?.push(value);
      },
      get type(): string {
        return currentSessions?.at(-1) ?? 'auto';
      },
    },
  },
});

export interface Harness {
  engine: Metronome;
  context: FakeAudioContext;
  beats: EngineBeat[];
  revocations: number[];
  vibrations: VibrationRecord[];
  sessions: string[];
  master(): FakeNode | undefined;
  survivors(): EngineBeat[];
  liveChannels(): number;
  readonly stateChanges: number;
  advance(seconds: number): void;
  gaps(): number[];
}

/**
 * Build an engine wired to a fake clock.
 *
 * @param t the test context, used for mock timers and cleanup
 * @param handlers engine callbacks to observe alongside the harness's own
 */
export async function createHarness(t: TestContext, handlers: MetronomeHandlers = {}): Promise<Harness> {
  const context = new FakeAudioContext();
  currentContext = context;
  currentVibrations = [];
  currentSessions = [];

  // Enabled before `start()`, so the pump's interval is a mock from the outset.
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval'] });

  const { createMetronome } = await import('../../src/platform/audio-engine.ts');

  const beats: EngineBeat[] = [];
  /** Beat counts handed back as revoked, in order. */
  const revocations: number[] = [];
  /** `beats` minus everything revoked: the run that is actually heard. */
  let heard: EngineBeat[] = [];
  let stateChanges = 0;
  const engine = createMetronome({
    onBeats: (batch) => {
      beats.push(...batch);
      heard.push(...batch);
      handlers.onBeats?.(batch);
    },
    onRevoke: (fromCount) => {
      revocations.push(fromCount);
      heard = heard.filter((beat) => beat.count < fromCount);
      handlers.onRevoke?.(fromCount);
    },
    onStateChange: () => {
      stateChanges += 1;
      handlers.onStateChange?.();
    },
  });

  t.after(() => {
    engine.stop();
    currentContext = null;
    currentVibrations = null;
    currentSessions = null;
  });

  /**
   * The engine's master gain: the one node wired straight to the destination.
   * Every beat gets its own gain into this, and revoking a beat disconnects
   * it, so counting what still arrives here counts the audio still committed.
   */
  function master(): FakeNode | undefined {
    return context.nodes.find(
      (node) => node.kind === 'gain' && node.outputs.includes(context.destination),
    );
  }

  return {
    engine,
    context,
    beats,
    revocations,
    vibrations: currentVibrations,
    sessions: currentSessions,
    master,

    /**
     * The beats that actually sounded, in time order.
     *
     * `beats` is every beat the engine ever announced, which after a tempo
     * change holds both a revoked entry and its replacement under the same
     * number. This drops the revoked ones, which is what any real listener
     * has to do, and is what most assertions want.
     */
    survivors() {
      return [...heard].sort((a, b) => a.time - b.time);
    },

    /** Per-beat channels still wired to the master, i.e. still able to sound. */
    liveChannels() {
      const target = master();
      return target ? context.nodes.filter((node) => node.outputs.includes(target)).length : 0;
    },
    get stateChanges() {
      return stateChanges;
    },

    /** Move the audio clock and the timers forward together. */
    advance(seconds: number) {
      const total = Math.round(seconds * 1000);
      for (let done = 0; done < total; done += STEP_MS) {
        const step = Math.min(STEP_MS, total - done);
        context._ms += step;
        t.mock.timers.tick(step);
      }
    },

    /**
     * Gaps between consecutive scheduled beats, which is where drift would show.
     *
     * Only meaningful for a run with no revocations in it. A tempo change
     * re-emits the beats it revoked, so `beats` then holds both the discarded
     * entry and its replacement and these gaps go negative. Reconstruct the
     * surviving run from `onRevoke` instead.
     */
    gaps() {
      return beats.slice(1).map((beat, i) => beat.time - beats[i].time);
    },
  };
}
