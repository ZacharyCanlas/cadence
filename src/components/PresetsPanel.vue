<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { PRESETS } from '../core/presets.ts';
import BaseSegmentedGroup, { type SegmentedItem } from './BaseSegmentedGroup.vue';

const { currentBpm, setBpm } = useMetronome();

const items: SegmentedItem[] = PRESETS.map((preset) => ({
  value: preset.bpm,
  label: preset.label,
  sub: String(preset.bpm),
}));

const hint = computed(() => {
  const preset = PRESETS.find((entry) => entry.bpm === currentBpm.value);
  return preset ? `${preset.label}. ${preset.hint}.` : 'Set by hand. Tap a preset to jump to a known target.';
});
</script>

<template>
  <section class="panel" aria-labelledby="presetsLabel">
    <h2 id="presetsLabel">Presets</h2>
    <BaseSegmentedGroup
      class="presets"
      aria-labelledby="presetsLabel"
      :items="items"
      :model-value="currentBpm"
      @select="(bpm) => setBpm(bpm)"
    />
    <p class="hint">{{ hint }}</p>
  </section>
</template>

<style scoped>
/* Styles the group component's root, which inherits this scope. */
.presets {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: repeat(3, 1fr);
}
</style>
