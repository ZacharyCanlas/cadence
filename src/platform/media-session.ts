/**
 * Lock screen and notification controls.
 *
 * Pairing this with the keep-alive element is what turns the app into
 * something the operating system recognises as a running player: a
 * notification you can stop from without unlocking, and a page the browser has
 * a reason to keep resident.
 */

import { capabilities } from './platform.ts';

export type MediaSessionHandlers = {
  onPlay: () => void;
  onStop: () => void;
}

export type MediaSessionControls = {
  readonly supported: boolean;
  activate(bpm: number): void;
  deactivate(): void;
  describe(bpm: number): void;
}

export function createMediaSession({ onPlay, onStop }: MediaSessionHandlers): MediaSessionControls {
  if (!capabilities.mediaSession) {
    return { supported: false, activate() {}, deactivate() {}, describe() {} };
  }

  const session = navigator.mediaSession;
  let handlersBound = false;

  function bind(): void {
    if (handlersBound) return;
    const actions: [MediaSessionAction, MediaSessionActionHandler][] = [
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

  function describe(bpm: number): void {
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
  }

  return {
    supported: true,
    activate(bpm: number) {
      bind();
      describe(bpm);
      session.playbackState = 'playing';
    },
    deactivate() {
      session.playbackState = 'paused';
    },
    describe,
  };
}
