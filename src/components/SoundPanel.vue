<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';
import { findVoice, VOICES } from '../core/sounds.ts';
import BaseSegmentedGroup, { type SegmentedItem } from './BaseSegmentedGroup.vue';

const { settings } = useSettings();
const { chooseVoice, setVolume } = useMetronome();

const items: SegmentedItem[] = VOICES.map((voice) => ({ value: voice.id, label: voice.label }));

const hint = computed(() => findVoice(settings.voice).hint);

function onVolume(event: Event) {
  setVolume(Number((event.target as HTMLInputElement).value));
}
</script>

<template>
  <section class="panel" aria-labelledby="soundLabel">
    <h2 id="soundLabel">Sound</h2>
    <BaseSegmentedGroup
      class="voices"
      aria-labelledby="soundLabel"
      :items="items"
      :model-value="settings.voice"
      @select="(id) => chooseVoice(String(id))"
    />
    <p class="hint">{{ hint }}</p>
    <!-- The volume slider means nothing when there is no sound to hear. -->
    <div class="row" :data-enabled="String(settings.click)">
      <label for="volume">Volume</label>
      <input
        id="volume"
        type="range"
        min="0"
        max="100"
        step="1"
        :value="settings.volume"
        aria-label="Sound volume"
        :disabled="!settings.click"
        @input="onVolume"
      >
      <output class="value" for="volume">{{ settings.volume }}</output>
    </div>
  </section>
</template>

<style scoped>
.voices {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr));
}
</style>
