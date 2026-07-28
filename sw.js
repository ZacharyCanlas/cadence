/**
 * Offline shell.
 *
 * Two strategies, chosen by what is being asked for:
 *
 * Navigations go to the network first and fall back to the cached page. That
 * way a deploy is picked up on the next launch with a connection, instead of
 * the old copy being pinned until someone remembers to bump a version string.
 *
 * Everything else is served from cache immediately and refreshed in the
 * background, so a run never waits on the network and the next launch has the
 * new file.
 */

const VERSION = 'cadence-v3';

/** Enough to boot the app with no network at all. */
const SHELL = [
  '.',
  'index.html',
  'styles.css',
  'manifest.json',
  'icon-180.png',
  'icon-192.png',
  'icon-512.png',
  'src/main.js',
  'src/audio-engine.js',
  'src/beat-grid.js',
  'src/keep-alive.js',
  'src/media-session.js',
  'src/platform.js',
  'src/presets.js',
  'src/pump.js',
  'src/ramp.js',
  'src/settings.js',
  'src/sounds.js',
  'src/tap-tempo.js',
  'src/ui.js',
  'src/wake-lock.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(VERSION)
      // addAll is all-or-nothing, so one bad path would leave the app with no
      // cache at all. Fetch them individually and keep what succeeds.
      .then((cache) => Promise.allSettled(SHELL.map((path) => cache.add(path))))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(VERSION).then((cache) => cache.put(request, copy));
          return response;
        })
        .catch(() => caches.match(request, { ignoreSearch: true }).then((hit) => hit || caches.match('index.html'))),
    );
    return;
  }

  event.respondWith(
    caches.match(request, { ignoreSearch: true }).then((hit) => {
      const network = fetch(request)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(VERSION).then((cache) => cache.put(request, copy));
          }
          return response;
        })
        .catch(() => hit);
      return hit || network;
    }),
  );
});
