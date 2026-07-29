<script setup lang="ts">
import { nextTick, ref } from 'vue';
import AccentPanel from './components/AccentPanel.vue';
import BeatRing from './components/BeatRing.vue';
import OutputPanel from './components/OutputPanel.vue';
import PresetsPanel from './components/PresetsPanel.vue';
import RampPanel from './components/RampPanel.vue';
import SoundPanel from './components/SoundPanel.vue';
import StatusPanel from './components/StatusPanel.vue';
import TapTempoButton from './components/TapTempoButton.vue';
import TempoControl from './components/TempoControl.vue';
import { useMetronome } from './composables/useMetronome.ts';
import { formatClock, formatSteps } from './core/format.ts';
import { capabilities } from './platform/platform.ts';

const { running, elapsed, stepCount, announcement, toggleRun } = useMetronome();

const statusOpen = ref(false);
const statusPanel = ref<InstanceType<typeof StatusPanel> | null>(null);

async function toggleStatus() {
  statusOpen.value = !statusOpen.value;
  if (statusOpen.value) {
    await nextTick();
    statusPanel.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
}
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="wordmark">CA<b>DEN</b>CE</div>
      <button
        class="ghost"
        :aria-expanded="statusOpen"
        aria-controls="statusPanel"
        @click="toggleStatus"
      >
        Status
      </button>
    </header>

    <main id="main">
      <section class="hero" aria-labelledby="heroLabel">
        <h1 id="heroLabel" class="sr-only">Cadence, a running metronome</h1>
        <BeatRing />
        <p class="session" :hidden="!running">
          <span>{{ formatClock(elapsed) }}</span>
          <span class="sep" aria-hidden="true">·</span>
          <span>{{ formatSteps(stepCount) }}</span>
        </p>
        <p class="live sr-only" role="status" aria-live="polite">{{ announcement }}</p>
      </section>

      <TempoControl />
      <TapTempoButton />
      <PresetsPanel />
      <SoundPanel />
      <AccentPanel />
      <OutputPanel />
      <RampPanel />
      <StatusPanel ref="statusPanel" :open="statusOpen" />
    </main>

    <div class="dock">
      <button
        class="go"
        :data-running="String(running)"
        :disabled="!capabilities.audio"
        @click="toggleRun"
      >
        {{ !capabilities.audio ? 'Web Audio unavailable' : running ? 'Stop' : 'Start' }}
      </button>
    </div>
  </div>
</template>
