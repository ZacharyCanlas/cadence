/**
 * The hand-written service worker that predates the Vite build cached under its
 * own version names ('cadence-v1'...'cadence-v3'). Workbox only cleans caches
 * it owns, so without this an upgraded install would carry those around
 * forever. Runs alongside Workbox's own activate handling.
 */
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key.startsWith('cadence-v')).map((key) => caches.delete(key))),
      ),
  );
});
