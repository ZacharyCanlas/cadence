<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';
import { ACCENTS } from '../core/presets.ts';
import BaseSegmentedGroup, { type SegmentedItem } from './BaseSegmentedGroup.vue';

const { settings } = useSettings();
const { chooseAccent } = useMetronome();

const items: SegmentedItem[] = ACCENTS.map((accent) => ({ value: accent.every, label: accent.label }));

const hint = computed(
  () => ACCENTS.find((accent) => accent.every === settings.accentEvery)?.hint ?? '',
);
</script>

<template>
  <section class="panel" aria-labelledby="accentLabel">
    <h2 id="accentLabel">Accent</h2>
    <BaseSegmentedGroup
      class="accents"
      aria-labelledby="accentLabel"
      :items="items"
      :model-value="settings.accentEvery"
      @select="(every) => chooseAccent(Number(every))"
    />
    <p class="hint">{{ hint }}</p>
  </section>
</template>

<style scoped>
.accents {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: repeat(auto-fill, minmax(5rem, 1fr));
}
</style>
