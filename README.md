# Cadence

A running-cadence metronome for the mobile browser that plays clicks (or vibration)
**over** your music instead of pausing it.

- **iPhone (Safari):** uses the Audio Session API (`navigator.audioSession.type = "ambient"`)
  so clicks mix with Spotify. Trade-off: iOS mutes ambient audio when the Silent
  switch is on — ringer must be on. A "Silent-mode override" (playback mode) exists
  for diagnosing, but it pauses other audio.
- **Android (Chrome):** no mixing API; Chrome may take audio focus and duck/pause
  music. The Vibrate mode is the reliable fallback — one haptic pulse per step.
- Web Audio lookahead scheduler for drift-free timing: clicks are placed on the
  audio-hardware clock from an anchored beat grid (`anchor + n × 60/bpm`, never
  accumulated), the scheduler pump runs in a Web Worker so main-thread jank and
  timer throttling can't stall it, and after a stall it skips to the next grid
  slot instead of bursting missed clicks. Screen Wake Lock while running (a
  suspended page can't tick — this is the browser-metronome trade-off).

## Stack

Vanilla HTML/CSS/JS, zero dependencies, no build step. Installable PWA
(`manifest.json` + `sw.js` cache-first service worker).

## Deploy (GitHub Pages)

1. Push to GitHub, enable Pages (Settings → Pages → deploy from `main`, root).
2. Open `https://<user>.github.io/cadence/` on your phone; add to home screen.
3. On every deploy, bump the `CACHE` version string in `sw.js` so phones drop the
   stale cached copy.

## Files

- `index.html` — the entire app
- `manifest.json`, `sw.js`, `icon-*.png` — PWA install + offline
