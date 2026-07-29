<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';
import { capabilities } from '../platform/platform.ts';

/**
 * The diagnostics panel: what the platform is actually doing right now, and
 * what to expect from it. Every row is derived state; the engine and wrappers
 * report, this renders.
 */

defineProps<{ open: boolean }>();

const { settings } = useSettings();
const { running, contextState, keepAlivePlaying, wakeLockHeld, usingWorkerPump, wakeLockSupported } =
  useMetronome();

interface Check {
  tone: 'good' | 'warn' | 'idle';
  title: string;
  detail: string;
}

const checks = computed<Check[]>(() => {
  const state = contextState.value;
  return [
    settings.background
      ? {
          tone: running.value ? (keepAlivePlaying.value ? 'good' : 'warn') : 'idle',
          title: keepAlivePlaying.value ? 'Running in the background' : 'Background mode is on',
          detail: keepAlivePlaying.value
            ? 'A media session is open, so the beat survives the screen locking and an app switch.'
            : 'The media session opens when you start.',
        }
      : {
          tone: 'warn',
          title: 'Background mode is off',
          detail: 'Your music has priority. Expect the beat to stop once the app is hidden.',
        },
    {
      tone: capabilities.audioSession ? (settings.background ? 'warn' : 'good') : 'idle',
      title: capabilities.audioSession
        ? settings.background
          ? 'Your music will pause'
          : 'Mixing with your music'
        : 'Mixing is up to the browser',
      detail: capabilities.audioSession
        ? settings.background
          ? 'Safari gives background audio sole use of the output. Turn background mode off to share it.'
          : 'Clicks layer over Spotify. The ringer switch has to be on for you to hear them.'
        : 'Android has no mixing control on the web. Start your music first, then start here and listen.',
    },
    {
      tone: state === 'running' ? 'good' : state === 'idle' ? 'idle' : 'warn',
      title: `Audio engine ${state}`,
      detail:
        state === 'running'
          ? 'Beats are being placed on the audio clock ahead of time.'
          : state === 'idle'
            ? 'Starts on your first tap. Browsers require a tap before any sound.'
            : 'Another app took the output, or the browser paused it. Tap Start again.',
    },
    {
      tone: usingWorkerPump ? 'good' : 'warn',
      title: usingWorkerPump ? 'Timing runs off the main thread' : 'Timing runs on the main thread',
      detail: usingWorkerPump
        ? 'A worker keeps the beat queue topped up even when the page is busy or hidden.'
        : 'Workers are unavailable here, so heavy pages could delay the queue.',
    },
    {
      tone: capabilities.vibrate ? 'good' : 'idle',
      title: capabilities.vibrate ? 'Vibration available' : 'No vibration on this browser',
      detail: capabilities.vibrate
        ? 'Usable on its own or alongside the sound.'
        : 'iPhone does not offer vibration to the web. Use a sound instead.',
    },
    {
      tone: wakeLockSupported ? (wakeLockHeld.value ? 'good' : 'idle') : 'idle',
      title: wakeLockSupported
        ? wakeLockHeld.value
          ? 'Screen staying awake'
          : 'Screen wake lock ready'
        : 'No wake lock on this browser',
      detail: wakeLockSupported
        ? 'Convenience only. The beat is built to carry on with the screen off.'
        : 'The screen will sleep on its own schedule. The beat carries on regardless.',
    },
  ];
});
</script>

<template>
  <section id="statusPanel" class="panel status" aria-labelledby="statusLabel" :hidden="!open">
    <h2 id="statusLabel">Status</h2>
    <ul class="checks">
      <li v-for="(check, index) in checks" :key="index" class="check" :data-tone="check.tone">
        <span class="dot" aria-hidden="true"></span>
        <span class="check-text">
          <b>{{ check.title }}</b>
          <span v-if="check.detail" class="check-detail">{{ check.detail }}</span>
        </span>
      </li>
    </ul>
    <p class="hint">
      Closing the app entirely stops the beat. No web app can keep making
      sound once it is closed, only while it is running in the background.
    </p>
  </section>
</template>
