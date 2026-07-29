/**
 * Browser APIs the app uses that lib.dom does not (yet) describe: Safari's
 * Audio Session API, the legacy webkit audio constructor, and the iOS
 * standalone flag.
 */

interface Navigator {
  audioSession?: { type: string };
  /** iOS Safari only: true when launched from the home screen. */
  standalone?: boolean;
}

interface Window {
  webkitAudioContext?: typeof AudioContext;
}
