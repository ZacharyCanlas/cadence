/**
 * A fake Web Audio environment, so the scheduler can be unit tested.
 *
 * `audio-engine.js` is the one module that is neither pure logic nor a thin
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
 * The globals are installed once, at module load, because `platform.js` reads
 * `window` and `navigator` when it is first evaluated and freezes the result.
 * A stable constructor is installed there and handed a fresh context per
 * harness, which keeps module caching from leaking one test into the next.
 */

/** Low enough to keep the noise buffer cheap to fill; nothing here cares about fidelity. */
const SAMPLE_RATE = 8000;
/** Clock slice for `advance`. Well under the 25ms pump interval, so no tick is skipped. */
const STEP_MS = 5;

class FakeParam {
  constructor(value = 0) {
    this.value = value;
    /** Every automation call, in order, as [kind, value, time]. */
    this.events = [];
  }

  setValueAtTime(value, time) {
    this.events.push(['set', value, time]);
    this.value = value;
    return this;
  }

  linearRampToValueAtTime(value, time) {
    this.events.push(['linear', value, time]);
    return this;
  }

  exponentialRampToValueAtTime(value, time) {
    this.events.push(['exponential', value, time]);
    return this;
  }

  cancelScheduledValues(time) {
    this.events.push(['cancel', null, time]);
    return this;
  }
}

class FakeNode {
  constructor(kind) {
    this.kind = kind;
    this.outputs = [];
    this.startedAt = null;
    this.stoppedAt = null;
  }

  /** Returns the target, which is what lets voices write `a.connect(b).connect(c)`. */
  connect(target) {
    this.outputs.push(target);
    return target;
  }

  disconnect(target) {
    if (target) this.outputs = this.outputs.filter((output) => output !== target);
    else this.outputs = [];
  }

  start(time) {
    this.startedAt = time;
  }

  stop(time) {
    this.stoppedAt = time;
  }
}

class FakeAudioContext {
  constructor() {
    this.sampleRate = SAMPLE_RATE;
    this.state = 'running';
    this.destination = new FakeNode('destination');
    this.onstatechange = null;
    /** Every node handed out, so a test can assert on what was built. */
    this.nodes = [];
    this.resumeCount = 0;
    this._ms = 0;
  }

  get currentTime() {
    return this._ms / 1000;
  }

  _track(node) {
    this.nodes.push(node);
    return node;
  }

  createGain() {
    const node = new FakeNode('gain');
    node.gain = new FakeParam(1);
    return this._track(node);
  }

  createOscillator() {
    const node = new FakeNode('oscillator');
    node.type = 'sine';
    node.frequency = new FakeParam(440);
    return this._track(node);
  }

  createBiquadFilter() {
    const node = new FakeNode('filter');
    node.type = 'lowpass';
    node.frequency = new FakeParam(350);
    node.Q = new FakeParam(1);
    return this._track(node);
  }

  createBufferSource() {
    const node = new FakeNode('bufferSource');
    node.buffer = null;
    return this._track(node);
  }

  createBuffer(channels, length) {
    return { length, numberOfChannels: channels, getChannelData: () => new Float32Array(length) };
  }

  resume() {
    this.resumeCount += 1;
    this.state = 'running';
    return Promise.resolve();
  }
}

/** The context the installed constructor hands out. Swapped per harness. */
let currentContext = null;
/** Vibration calls, stamped with the audio time at which they landed. */
let currentVibrations = null;
/** Audio session types set through `platform.js`, in order. */
let currentSessions = null;

class InstalledAudioContext {
  constructor() {
    if (!currentContext) throw new Error('No harness is active; call createHarness first');
    return currentContext;
  }
}

// Installed once, before anything imports `platform.js`.
globalThis.window = { AudioContext: InstalledAudioContext, webkitAudioContext: undefined };
Object.defineProperty(globalThis, 'navigator', {
  configurable: true,
  writable: true,
  value: {
    userAgent: 'harness',
    maxTouchPoints: 0,
    vibrate(pattern) {
      currentVibrations?.push({ at: currentContext ? currentContext.currentTime : 0, pattern });
      return true;
    },
    audioSession: {
      set type(value) {
        currentSessions?.push(value);
      },
      get type() {
        return currentSessions?.at(-1) ?? 'auto';
      },
    },
  },
});

/**
 * Build an engine wired to a fake clock.
 *
 * @param {import('node:test').TestContext} t
 * @returns {Promise<object>} the engine plus the observations a test asserts on
 */
export async function createHarness(t) {
  const context = new FakeAudioContext();
  currentContext = context;
  currentVibrations = [];
  currentSessions = [];

  // Enabled before `start()`, so the pump's interval is a mock from the outset.
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval'] });

  const { createMetronome } = await import('../../src/audio-engine.js');

  const beats = [];
  let stateChanges = 0;
  const engine = createMetronome({
    onBeats: (batch) => beats.push(...batch),
    onStateChange: () => {
      stateChanges += 1;
    },
  });

  t.after(() => {
    engine.stop();
    currentContext = null;
    currentVibrations = null;
    currentSessions = null;
  });

  return {
    engine,
    context,
    beats,
    vibrations: currentVibrations,
    sessions: currentSessions,
    get stateChanges() {
      return stateChanges;
    },

    /** Move the audio clock and the timers forward together. */
    advance(seconds) {
      const total = Math.round(seconds * 1000);
      for (let done = 0; done < total; done += STEP_MS) {
        const step = Math.min(STEP_MS, total - done);
        context._ms += step;
        t.mock.timers.tick(step);
      }
    },

    /** Gaps between consecutive scheduled beats, which is where drift would show. */
    gaps() {
      return beats.slice(1).map((beat, i) => beat.time - beats[i].time);
    },
  };
}
