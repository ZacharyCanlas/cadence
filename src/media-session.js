/**
 * Lock screen and notification controls.
 *
 * Pairing this with the keep-alive element is what turns the app into
 * something the operating system recognises as a running player: a
 * notification you can stop from without unlocking, and a page the browser has
 * a reason to keep resident.
 */

import { capabilities } from './platform.js';

export function createMediaSession({ onPlay, onStop }) {
  if (!capabilities.mediaSession) {
    return { supported: false, activate() {}, deactivate() {}, describe() {} };
  }

  const session = navigator.mediaSession;
  let handlersBound = false;

  function bind() {
    if (handlersBound) return;
    const actions = [
      ['play', onPlay],
      ['pause', onStop],
      ['stop', onStop],
    ];
    for (const [action, handler] of actions) {
      try {
        session.setActionHandler(action, handler);
      } catch {
        // Not every action is supported everywhere; the rest still bind.
      }
    }
    handlersBound = true;
  }

  return {
    supported: true,
    activate(bpm) {
      bind();
      this.describe(bpm);
      session.playbackState = 'playing';
    },
    deactivate() {
      session.playbackState = 'paused';
    },
    describe(bpm) {
      if (!('MediaMetadata' in window)) return;
      session.metadata = new window.MediaMetadata({
        title: `${bpm} steps per minute`,
        artist: 'Cadence',
        album: 'Running metronome',
        artwork: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      });
    },
  };
}
