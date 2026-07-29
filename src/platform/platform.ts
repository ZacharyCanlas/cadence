/**
 * Feature detection and the one platform API that needs a wrapper: the Audio
 * Session API, which today only Safari implements.
 */

const AudioContextCtor =
  typeof window !== 'undefined' ? (window.AudioContext ?? window.webkitAudioContext ?? null) : null;

export const capabilities = Object.freeze({
  audio: Boolean(AudioContextCtor),
  audioSession: typeof navigator !== 'undefined' && 'audioSession' in navigator,
  vibrate: typeof navigator !== 'undefined' && 'vibrate' in navigator,
  wakeLock: typeof navigator !== 'undefined' && 'wakeLock' in navigator,
  mediaSession: typeof navigator !== 'undefined' && 'mediaSession' in navigator,
  worker: typeof Worker !== 'undefined' && typeof Blob !== 'undefined',
});

export function createAudioContext(): AudioContext {
  if (!AudioContextCtor) throw new Error('Web Audio is not available in this browser');
  return new AudioContextCtor();
}

/**
 * Audio session types we use.
 *
 * 'ambient'  mixes with whatever else is playing, but iOS silences it with the
 *            ringer switch and stops it once the app is backgrounded.
 * 'playback' keeps running with the screen off and the app in the background,
 *            and ignores the ringer switch, but iOS gives it exclusive use of
 *            the output, so other players are paused.
 *
 * There is no web-exposed equivalent of the native "playback + mix with
 * others" combination, so this is a genuine either/or on iOS. Android ignores
 * the call entirely and mixes by default.
 */
export const SESSION_MIX = 'ambient';
export const SESSION_BACKGROUND = 'playback';

export type AudioSessionType = typeof SESSION_MIX | typeof SESSION_BACKGROUND;

export function setAudioSession(type: AudioSessionType): boolean {
  if (!capabilities.audioSession) return false;
  try {
    navigator.audioSession!.type = type;
    return true;
  } catch {
    return false;
  }
}

export function isStandalone(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    window.matchMedia?.('(display-mode: standalone)').matches === true ||
    window.navigator.standalone === true
  );
}

export function isIOS(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  return /iPad|iPhone|iPod/.test(ua) || (/Mac/.test(ua) && navigator.maxTouchPoints > 1);
}
