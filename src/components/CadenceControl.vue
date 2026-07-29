<script setup lang="ts">
import { useMetronome } from '../composables/useMetronome.ts';
import { MAX_BPM, MIN_BPM } from '../core/beat-grid.ts';

const { currentBpm, setBpm } = useMetronome();

function onSlider(event: Event) {
  setBpm((event.target as HTMLInputElement).value);
}
</script>

<template>
  <section class="tempo" aria-label="Cadence">
    <button class="step" aria-label="One step per minute slower" @click="setBpm(currentBpm - 1)">&minus;</button>
    <input
      type="range"
      :min="MIN_BPM"
      :max="MAX_BPM"
      step="1"
      :value="currentBpm"
      aria-label="Steps per minute"
      @input="onSlider"
    >
    <button class="step" aria-label="One step per minute faster" @click="setBpm(currentBpm + 1)">+</button>
  </section>
</template>

<style scoped>
.tempo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.step {
  flex: 0 0 3.5rem;
  height: 3.5rem;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  font-size: var(--text-xl);
  font-weight: 500;
  line-height: 1;
  transition: background var(--fast) var(--ease), border-color var(--fast) var(--ease);
}

.step:hover {
  border-color: var(--line-strong);
}

.step:active {
  background: var(--raise);
  transform: translateY(1px);
}
</style>
