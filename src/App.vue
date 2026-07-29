<script setup lang="ts">
import { nextTick, ref, useTemplateRef } from 'vue';
import AccentPanel from './components/AccentPanel.vue';
import BeatRing from './components/BeatRing.vue';
import OutputPanel from './components/OutputPanel.vue';
import PresetsPanel from './components/PresetsPanel.vue';
import RampPanel from './components/RampPanel.vue';
import SoundPanel from './components/SoundPanel.vue';
import StatusPanel from './components/StatusPanel.vue';
import TapTempoButton from './components/TapTempoButton.vue';
import CadenceControl from './components/CadenceControl.vue';
import { useMetronome } from './composables/useMetronome.ts';
import { formatClock, formatSteps } from './core/format.ts';
import { capabilities } from './platform/platform.ts';

const { running, elapsed, stepCount, announcement, toggleRun } = useMetronome();

const statusOpen = ref(false);
const statusPanel = useTemplateRef('statusPanel');

async function toggleStatus() {
  statusOpen.value = !statusOpen.value;
  if (statusOpen.value) {
    await nextTick();
    statusPanel.value?.scrollIntoView();
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

      <CadenceControl />
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

<style scoped>
/* --- shell --------------------------------------------------------------- */

.app {
  max-width: var(--page);
  margin-inline: auto;
  padding-inline: var(--space-4);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block: var(--space-4) var(--space-2);
  padding-top: max(var(--space-4), env(safe-area-inset-top));
}

.wordmark {
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.26em;
  text-transform: uppercase;
}

.wordmark b {
  color: var(--accent);
}

.ghost {
  min-height: var(--tap);
  padding-inline: var(--space-3);
  border-radius: var(--radius-pill);
  color: var(--ink-2);
  font-size: var(--text-sm);
  font-weight: 550;
  transition: color var(--fast) var(--ease), background var(--fast) var(--ease);
}

.ghost:hover {
  color: var(--ink);
  background: var(--raise);
}

.ghost[aria-expanded='true'] {
  color: var(--accent);
  background: var(--accent-wash);
}

main {
  /* Room for the dock, which floats over the end of the scroll. */
  padding-bottom: calc(6.5rem + env(safe-area-inset-bottom));
}

/* --- the instrument's stage ---------------------------------------------- */

.hero {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding-block: var(--space-4) var(--space-5);
}

.session {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-family: var(--mono);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--ink-2);
}

.session .sep {
  color: var(--line-strong);
}

/* --- dock ---------------------------------------------------------------- */

.dock {
  position: fixed;
  inset-inline: 0;
  bottom: 0;
  z-index: 10;
  padding: var(--space-3) var(--space-4);
  padding-bottom: max(var(--space-3), env(safe-area-inset-bottom));
  background: color-mix(in srgb, var(--ground) 88%, transparent);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--line);
  box-shadow: var(--dock-shadow);
}

@supports not (backdrop-filter: blur(12px)) {
  .dock {
    background: var(--ground);
  }
}

.go {
  display: block;
  width: 100%;
  max-width: var(--page);
  margin-inline: auto;
  min-height: 3.75rem;
  border-radius: var(--radius-lg);
  background: var(--accent);
  color: var(--accent-ink);
  font-size: var(--text-md);
  font-weight: 700;
  letter-spacing: 0.01em;
  box-shadow: var(--shadow-2);
  transition: background var(--fast) var(--ease), transform var(--fast) var(--ease);
}

.go:active {
  transform: translateY(1px);
}

.go[data-running='true'] {
  background: var(--ink);
  color: var(--ground);
}

.go:disabled {
  background: var(--raise);
  color: var(--ink-2);
  box-shadow: none;
}
</style>
