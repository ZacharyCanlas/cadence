import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  // Relative base keeps the build deployable from any path, which is how the
  // pre-Vite app worked on GitHub Pages.
  base: './',
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon-180.png', 'icon-192.png', 'icon-512.png'],
      manifest: {
        name: 'Cadence',
        short_name: 'Cadence',
        description: 'Running metronome that plays over your music',
        start_url: '.',
        scope: '.',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0F1114',
        theme_color: '#0F1114',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,webmanifest}'],
        // Clears the caches left behind by the hand-written pre-Vite worker.
        importScripts: ['sw-legacy-cleanup.js'],
        cleanupOutdatedCaches: true,
      },
    }),
  ],
});
