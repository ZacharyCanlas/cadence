/**
 * Background keep-alive.
 *
 * Browsers suspend a page once it stops being audible, and Web Audio on its
 * own does not always count. A looping media element does: it registers as
 * real media playback, which keeps the page resident, keeps its timers off the
 * throttled path, and puts the app on the lock screen through the Media
 * Session API.
 *
 * The loop is a fraction of a second of near-silence generated at runtime, so
 * there is no binary asset to ship. It is deliberately not `muted` and not
 * zero volume: a silent element is treated as inaudible by some browsers and
 * gets suspended along with the rest of the page.
 */

const SAMPLE_RATE = 8000;
const SECONDS = 1;
/** A couple of LSBs of dither. Inaudible, but not digital silence. */
const AMPLITUDE = 3;

function encodeWav(samples: Int16Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const writeAscii = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i += 1) view.setUint8(offset + i, text.charCodeAt(i));
  };

  writeAscii(0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  view.setUint32(16, 16, true); // PCM header size
  view.setUint16(20, 1, true); // PCM format
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); // byte rate
  view.setUint16(32, 2, true); // block align
  view.setUint16(34, 16, true); // bits per sample
  writeAscii(36, 'data');
  view.setUint32(40, samples.length * 2, true);

  for (let i = 0; i < samples.length; i += 1) {
    view.setInt16(44 + i * 2, samples[i], true);
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

function buildLoopUrl(): string {
  const samples = new Int16Array(SAMPLE_RATE * SECONDS);
  for (let i = 0; i < samples.length; i += 1) {
    samples[i] = i % 2 === 0 ? AMPLITUDE : -AMPLITUDE;
  }
  return URL.createObjectURL(encodeWav(samples, SAMPLE_RATE));
}

export interface KeepAlive {
  /** Must be called from a user gesture, same as any media playback. */
  start(): Promise<boolean>;
  stop(): void;
  readonly playing: boolean;
  /** The element the Media Session attaches to, if one exists yet. */
  readonly element: HTMLAudioElement | null;
  dispose(): void;
}

export function createKeepAlive(): KeepAlive {
  let element: HTMLAudioElement | null = null;
  let url: string | null = null;

  function ensureElement(): HTMLAudioElement {
    if (element) return element;
    url = buildLoopUrl();
    element = new Audio(url);
    element.loop = true;
    element.preload = 'auto';
    element.volume = 1; // the samples themselves are near-silent
    element.setAttribute('playsinline', '');
    // Keeps the element out of the accessibility tree and off screen readers.
    element.setAttribute('aria-hidden', 'true');
    return element;
  }

  function stop(): void {
    if (!element) return;
    element.pause();
    element.currentTime = 0;
  }

  return {
    async start() {
      const audio = ensureElement();
      try {
        await audio.play();
        return true;
      } catch {
        return false; // autoplay refused; the engine still runs, just not backgrounded
      }
    },
    stop,
    get playing() {
      return Boolean(element) && !element!.paused;
    },
    get element() {
      return element;
    },
    dispose() {
      stop();
      if (url) URL.revokeObjectURL(url);
      element = null;
      url = null;
    },
  };
}
