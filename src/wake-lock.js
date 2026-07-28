/**
 * Screen wake lock.
 *
 * This is a convenience, not the thing holding the beat together: the audio
 * engine is built to keep running with the screen off. Its only job is to stop
 * the display sleeping while you are looking at the app, and the system drops
 * it on its own whenever the page is hidden, so it gets re-taken on return.
 */

import { capabilities } from './platform.js';

export function createWakeLock(onChange = () => {}) {
  let sentinel = null;

  function notify() {
    onChange(sentinel !== null);
  }

  return {
    supported: capabilities.wakeLock,
    get held() {
      return sentinel !== null;
    },
    async request() {
      if (!capabilities.wakeLock || sentinel) return false;
      try {
        sentinel = await navigator.wakeLock.request('screen');
        sentinel.addEventListener('release', () => {
          sentinel = null;
          notify();
        });
        notify();
        return true;
      } catch {
        sentinel = null;
        notify();
        return false;
      }
    },
    async release() {
      if (!sentinel) return;
      const held = sentinel;
      sentinel = null;
      notify();
      try {
        await held.release();
      } catch {
        // Already gone, which is the state we wanted anyway.
      }
    },
  };
}
