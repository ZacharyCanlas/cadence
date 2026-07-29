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
