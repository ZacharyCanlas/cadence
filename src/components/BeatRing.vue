<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';

/**
 * The pulsing ring, its beat markers, and the big number. The flash is
 * imperative on purpose: the animation has to restart on every beat, and the
 * classList-remove / reflow / classList-add cycle is the one reliable way to
 * do that. Everything else on the ring is ordinary reactive rendering.
 */

const { settings } = useSettings();
const { running, currentBpm, onBeat } = useMetronome();

const ring = ref<HTMLDivElement | null>(null);
/** Position of the most recent beat within the accent cycle, -1 before any. */
const activeDot = ref(-1);

let unsubscribe: (() => void) | null = null;

onMounted(() => {
  unsubscribe = onBeat((beat) => {
    const node = ring.value;
    if (!node) return;
    node.classList.remove('tick', 'accent');
    void node.offsetWidth; // restart the animation rather than let it continue
    node.classList.add('tick');
    if (beat.accent) node.classList.add('accent');
    if (settings.accentEvery > 0) activeDot.value = beat.count % settings.accentEvery;
  });
});

onBeforeUnmount(() => unsubscribe?.());

watch(running, (isRunning) => {
  if (!isRunning) ring.value?.classList.remove('tick', 'accent');
});

// A new accent cycle redraws the markers, so none of them starts lit.
watch(
  () => settings.accentEvery,
  () => {
    activeDot.value = -1;
  },
);
</script>

<template>
  <div ref="ring" class="ring" aria-hidden="true">
    <div class="beats">
      <span
        v-for="position in settings.accentEvery"
        :key="position"
        class="beat-dot"
        :style="{ transform: `rotate(${((position - 1) / settings.accentEvery) * 360}deg)` }"
        :data-accent="String(position === 1)"
        :data-on="String(position - 1 === activeDot)"
      />
    </div>
    <div class="readout">
      <div class="bpm">{{ currentBpm }}</div>
      <div class="unit">steps per minute</div>
    </div>
  </div>
</template>
