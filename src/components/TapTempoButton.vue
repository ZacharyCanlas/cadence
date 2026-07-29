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
