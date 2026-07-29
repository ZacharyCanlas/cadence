<script setup lang="ts">
import { ref } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { createTapTempo } from '../core/tap-tempo.ts';

const { setBpm } = useMetronome();
const tapTempo = createTapTempo();

const hint = ref('Tap along as you run to read your cadence');

function onTap() {
  const reading = tapTempo.tap(performance.now());
  if (reading.bpm === null) {
    hint.value = `Keep tapping, ${reading.taps} so far`;
    return;
  }
  setBpm(reading.bpm);
  hint.value = `Reading ${reading.bpm} steps per minute`;
}
</script>

<template>
  <button class="tap" @click="onTap">
    <span class="tap-label">Tap your stride</span>
    <span class="tap-hint">{{ hint }}</span>
  </button>
</template>

<style scoped>
.tap {
  display: grid;
  gap: 0.1rem;
  width: 100%;
  min-height: var(--tap);
  padding: var(--space-3);
  margin-bottom: var(--space-6);
  border-radius: var(--radius);
  border: 1px dashed var(--line-strong);
  color: var(--ink-2);
  text-align: center;
  transition: border-color var(--fast) var(--ease), color var(--fast) var(--ease);
}

.tap:hover,
.tap:active {
  border-color: var(--accent);
  color: var(--ink);
}

.tap-label {
  font-weight: 600;
  color: var(--ink);
}

.tap-hint {
  font-size: var(--text-sm);
  color: var(--ink-2);
}
</style>
