/**
 * Persisted user settings. Storage is injected so the validation rules can be
 * tested without a browser, and so a blocked or full localStorage degrades to
 * in-memory defaults instead of throwing.
 */

import { clampBpm, DEFAULT_BPM } from './beat-grid.js';
import { normalizeAccent } from './presets.js';
import { clampMinutes } from './ramp.js';
import { DEFAULT_VOICE, voiceIds } from './sounds.js';

const KEY = 'cadence.settings.v1';

export const DEFAULTS = Object.freeze({
  bpm: DEFAULT_BPM,
  volume: 70,
  click: true,
  vibrate: false,
  /** Hold a media session so the beat survives the screen locking. */
  background: true,
  voice: DEFAULT_VOICE,
  /** Beats between accents. 0 turns the accent off. */
  accentEvery: 0,
  rampOn: false,
  rampTo: 180,
  rampMinutes: 10,
});

function clampVolume(value) {
  const rounded = Math.round(Number(value));
  if (!Number.isFinite(rounded)) return DEFAULTS.volume;
  return Math.min(100, Math.max(0, rounded));
}

function asBool(value, fallback) {
  return typeof value === 'boolean' ? value : fallback;
}

/** Coerce anything (old versions, hand-edited storage, junk) into a valid shape. */
export function normalize(raw) {
  const input = raw && typeof raw === 'object' ? raw : {};
  const settings = {
    bpm: clampBpm(input.bpm ?? DEFAULTS.bpm),
    volume: clampVolume(input.volume ?? DEFAULTS.volume),
    click: asBool(input.click, DEFAULTS.click),
    vibrate: asBool(input.vibrate, DEFAULTS.vibrate),
    background: asBool(input.background, DEFAULTS.background),
    voice: voiceIds().includes(input.voice) ? input.voice : DEFAULTS.voice,
    accentEvery: normalizeAccent(input.accentEvery ?? DEFAULTS.accentEvery),
    rampOn: asBool(input.rampOn, DEFAULTS.rampOn),
    rampTo: clampBpm(input.rampTo ?? DEFAULTS.rampTo),
    rampMinutes: clampMinutes(input.rampMinutes ?? DEFAULTS.rampMinutes),
  };
  // Silence on both channels would look like a broken app, so keep the click.
  if (!settings.click && !settings.vibrate) settings.click = true;
  return settings;
}

export function load(storage) {
  try {
    return normalize(JSON.parse(storage.getItem(KEY)));
  } catch {
    return normalize(null);
  }
}

export function save(storage, settings) {
  try {
    storage.setItem(KEY, JSON.stringify(normalize(settings)));
    return true;
  } catch {
    return false; // private mode, quota, or storage disabled entirely
  }
}

/** In-memory stand-in used when localStorage is unavailable. */
export function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  };
}

export function safeLocalStorage() {
  try {
    const probe = '__cadence_probe__';
    window.localStorage.setItem(probe, '1');
    window.localStorage.removeItem(probe);
    return window.localStorage;
  } catch {
    return memoryStorage();
  }
}
