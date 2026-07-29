/**
 * The sound library.
 *
 * Every voice is synthesised from oscillators and a noise buffer rather than
 * loaded from a file. That keeps the app a few kilobytes, keeps it working
 * offline the moment it installs, and means a voice can be re-pitched for the
 * accent beat instead of needing a second recording.
 *
 * Each voice exposes `play(context, destination, time, options)` and schedules
 * itself on the audio clock at `time`. Nothing here reads the current time, so
 * a voice can be queued seconds ahead without changing how it sounds.
 */

export interface VoicePlayOptions {
  accent?: boolean;
}

export interface Voice {
  id: string;
  label: string;
  hint: string;
  play(context: AudioContext, destination: AudioNode, time: number, options?: VoicePlayOptions): void;
}

const noiseBuffers = new WeakMap<AudioContext, AudioBuffer>();

/** One second of white noise per context, reused by every percussive voice. */
function noiseBuffer(context: AudioContext): AudioBuffer {
  let buffer = noiseBuffers.get(context);
  if (buffer) return buffer;
  buffer = context.createBuffer(1, context.sampleRate, context.sampleRate);
  const channel = buffer.getChannelData(0);
  for (let i = 0; i < channel.length; i += 1) channel[i] = Math.random() * 2 - 1;
  noiseBuffers.set(context, buffer);
  return buffer;
}

function noiseSource(context: AudioContext, time: number, duration: number): AudioBufferSourceNode {
  const source = context.createBufferSource();
  source.buffer = noiseBuffer(context);
  // A random offset stops repeated hits sounding like an identical loop.
  source.start(time, Math.random() * 0.5, duration);
  source.stop(time + duration);
  return source;
}

/** Percussive envelope: instant attack, exponential tail. */
function decay(context: AudioContext, time: number, peak: number, seconds: number): GainNode {
  const gain = context.createGain();
  gain.gain.setValueAtTime(peak, time);
  gain.gain.exponentialRampToValueAtTime(0.0001, time + seconds);
  return gain;
}

interface ToneShape {
  type: OscillatorType;
  from: number;
  to: number;
  sweep: number;
  peak: number;
  length: number;
}

function tone(context: AudioContext, time: number, { type, from, to, sweep, peak, length }: ToneShape): GainNode {
  const osc = context.createOscillator();
  osc.type = type;
  osc.frequency.setValueAtTime(from, time);
  if (to && to !== from) osc.frequency.exponentialRampToValueAtTime(to, time + sweep);
  const env = decay(context, time, peak, length);
  osc.connect(env);
  osc.start(time);
  osc.stop(time + length + 0.02);
  return env;
}

/** Accent beats are pitched up rather than just made louder, so they cut through music. */
const ACCENT_PITCH = 1.5;
const ACCENT_GAIN = 1.35;

function pitch(base: number, accent: boolean): number {
  return accent ? base * ACCENT_PITCH : base;
}

function level(base: number, accent: boolean): number {
  return accent ? base * ACCENT_GAIN : base;
}

export const VOICES: readonly Voice[] = [
  {
    id: 'click',
    label: 'Click',
    hint: 'Dry and sharp. Cuts through traffic and wind.',
    play(context, destination, time, { accent = false } = {}) {
      tone(context, time, {
        type: 'square',
        from: pitch(1250, accent),
        to: pitch(850, accent),
        sweep: 0.02,
        peak: level(0.5, accent),
        length: 0.045,
      }).connect(destination);
    },
  },
  {
    id: 'woodblock',
    label: 'Woodblock',
    hint: 'Warm and organic. The classic metronome sound.',
    play(context, destination, time, { accent = false } = {}) {
      tone(context, time, {
        type: 'triangle',
        from: pitch(1050, accent),
        to: pitch(760, accent),
        sweep: 0.03,
        peak: level(0.75, accent),
        length: 0.07,
      }).connect(destination);

      // A whisper of noise gives the hit its wooden knock.
      const knock = context.createBiquadFilter();
      knock.type = 'bandpass';
      knock.frequency.setValueAtTime(pitch(1800, accent), time);
      knock.Q.value = 2;
      const env = decay(context, time, level(0.3, accent), 0.02);
      noiseSource(context, time, 0.03).connect(knock).connect(env).connect(destination);
    },
  },
  {
    id: 'beep',
    label: 'Beep',
    hint: 'A clean tone. Easiest to pick out under headphones.',
    play(context, destination, time, { accent = false } = {}) {
      tone(context, time, {
        type: 'sine',
        from: pitch(880, accent),
        to: pitch(880, accent),
        sweep: 0,
        peak: level(0.6, accent),
        length: 0.09,
      }).connect(destination);
    },
  },
  {
    id: 'cowbell',
    label: 'Cowbell',
    hint: 'Metallic and loud. Built for noisy roads.',
    play(context, destination, time, { accent = false } = {}) {
      // Two detuned squares through a bandpass, the classic drum-machine recipe.
      const band = context.createBiquadFilter();
      band.type = 'bandpass';
      band.frequency.setValueAtTime(pitch(2640, accent), time);
      band.Q.value = 1.2;
      const env = decay(context, time, level(0.4, accent), 0.16);

      for (const frequency of [587.3, 845.0]) {
        const osc = context.createOscillator();
        osc.type = 'square';
        osc.frequency.setValueAtTime(pitch(frequency, accent), time);
        osc.connect(band);
        osc.start(time);
        osc.stop(time + 0.18);
      }
      band.connect(env).connect(destination);
    },
  },
  {
    id: 'rimshot',
    label: 'Rimshot',
    hint: 'Short and percussive. Sits neatly behind a track.',
    play(context, destination, time, { accent = false } = {}) {
      tone(context, time, {
        type: 'triangle',
        from: pitch(420, accent),
        to: pitch(220, accent),
        sweep: 0.02,
        peak: level(0.7, accent),
        length: 0.04,
      }).connect(destination);

      const snap = context.createBiquadFilter();
      snap.type = 'highpass';
      snap.frequency.value = 2200;
      const env = decay(context, time, level(0.35, accent), 0.03);
      noiseSource(context, time, 0.04).connect(snap).connect(env).connect(destination);
    },
  },
  {
    id: 'kick',
    label: 'Kick drum',
    hint: 'Low and felt more than heard. Good with music playing.',
    play(context, destination, time, { accent = false } = {}) {
      tone(context, time, {
        type: 'sine',
        from: accent ? 190 : 160,
        to: accent ? 55 : 48,
        sweep: 0.09,
        peak: level(0.95, accent),
        length: 0.22,
      }).connect(destination);
    },
  },
  {
    id: 'hihat',
    label: 'Hi-hat',
    hint: 'Crisp and light. Stays out of the way of vocals.',
    play(context, destination, time, { accent = false } = {}) {
      const high = context.createBiquadFilter();
      high.type = 'highpass';
      high.frequency.value = accent ? 7000 : 8500;
      const env = decay(context, time, level(0.35, accent), accent ? 0.09 : 0.04);
      noiseSource(context, time, 0.1).connect(high).connect(env).connect(destination);
    },
  },
  {
    id: 'kit',
    label: 'Drum kit',
    hint: 'Kick on the accent, hi-hat between. A groove to run to.',
    play(context, destination, time, { accent = false } = {}) {
      const voices = Object.fromEntries(VOICES.map((voice) => [voice.id, voice]));
      if (accent) voices.kick.play(context, destination, time, { accent: false });
      else voices.hihat.play(context, destination, time, { accent: false });
    },
  },
];

export const DEFAULT_VOICE = 'click';

export function findVoice(id: string): Voice {
  return (
    VOICES.find((voice) => voice.id === id) ?? (VOICES.find((voice) => voice.id === DEFAULT_VOICE) as Voice)
  );
}

export function voiceIds(): string[] {
  return VOICES.map((voice) => voice.id);
}
