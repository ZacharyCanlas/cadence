/**
 * Persisted user settings. Storage is injected so the validation rules can be
 * tested without a browser, and so a blocked or full localStorage degrades to
 * in-memory defaults instead of throwing.
 */

import { clampBpm, DEFAULT_BPM } from './beat-grid.ts';
import { normalizeAccent } from './presets.ts';
import { clampMinutes } from './ramp.ts';
import { DEFAULT_VOICE, voiceIds } from './sounds.ts';

const KEY = 'cadence.settings.v1';

export interface Settings {
  bpm: number;
  volume: number;
  click: boolean;
  vibrate: boolean;
  /** Hold a media session so the beat survives the screen locking. */
  background: boolean;
  voice: string;
  /** Beats between accents. 0 turns the accent off. */
  accentEvery: number;
  rampOn: boolean;
  rampTo: number;
  rampMinutes: number;
}

/** The slice of the Storage interface the app relies on, so a Map can stand in. */
export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export const DEFAULTS: Readonly<Settings> = Object.freeze({
  bpm: DEFAULT_BPM,
  volume: 70,
  click: true,
  vibrate: false,
  background: true,
  voice: DEFAULT_VOICE,
  accentEvery: 0,
  rampOn: false,
  rampTo: 180,
  rampMinutes: 10,
});

function clampVolume(value: unknown): number {
  const rounded = Math.round(Number(value));
  if (!Number.isFinite(rounded)) return DEFAULTS.volume;
  return Math.min(100, Math.max(0, rounded));
}

function asBool(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

/** Coerce anything (old versions, hand-edited storage, junk) into a valid shape. */
export function normalize(raw: unknown): Settings {
  const input = (raw && typeof raw === 'object' ? raw : {}) as Partial<Record<keyof Settings, unknown>>;
  const settings: Settings = {
    bpm: clampBpm(input.bpm ?? DEFAULTS.bpm),
    volume: clampVolume(input.volume ?? DEFAULTS.volume),
    click: asBool(input.click, DEFAULTS.click),
    vibrate: asBool(input.vibrate, DEFAULTS.vibrate),
    background: asBool(input.background, DEFAULTS.background),
    voice: typeof input.voice === 'string' && voiceIds().includes(input.voice) ? input.voice : DEFAULTS.voice,
    accentEvery: normalizeAccent(input.accentEvery ?? DEFAULTS.accentEvery),
    rampOn: asBool(input.rampOn, DEFAULTS.rampOn),
    rampTo: clampBpm(input.rampTo ?? DEFAULTS.rampTo),
    rampMinutes: clampMinutes(input.rampMinutes ?? DEFAULTS.rampMinutes),
  };
  // Silence on both channels would look like a broken app, so keep the click.
  if (!settings.click && !settings.vibrate) settings.click = true;
  return settings;
}

export function load(storage: StorageLike): Settings {
  try {
    return normalize(JSON.parse(storage.getItem(KEY) ?? 'null'));
  } catch {
    return normalize(null);
  }
}

export function save(storage: StorageLike, settings: Settings): boolean {
  try {
    storage.setItem(KEY, JSON.stringify(normalize(settings)));
    return true;
  } catch {
    return false; // private mode, quota, or storage disabled entirely
  }
}

/** In-memory stand-in used when localStorage is unavailable. */
export function memoryStorage(): StorageLike {
  const map = new Map<string, string>();
  return {
    getItem: (k) => (map.has(k) ? (map.get(k) as string) : null),
    setItem: (k, v) => {
      map.set(k, String(v));
    },
    removeItem: (k) => {
      map.delete(k);
    },
  };
}

export function safeLocalStorage(): StorageLike {
  try {
    const probe = '__cadence_probe__';
    window.localStorage.setItem(probe, '1');
    window.localStorage.removeItem(probe);
    return window.localStorage;
  } catch {
    return memoryStorage();
  }
}
