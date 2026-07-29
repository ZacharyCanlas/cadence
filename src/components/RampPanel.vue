<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';
import { MAX_BPM, MIN_BPM } from '../core/beat-grid.ts';
import { MAX_RAMP_MINUTES, MIN_RAMP_MINUTES, rampDone, rampProgress } from '../core/ramp.ts';
import BaseToggleSwitch from './BaseToggleSwitch.vue';

const { settings, update } = useSettings();
const { running, elapsed, setRampOn } = useMetronome();

const progress = computed(() => rampProgress(settings.rampMinutes, elapsed.value));

const status = computed(() =>
  rampDone(settings.rampMinutes, elapsed.value)
    ? `Holding at ${settings.rampTo} steps per minute`
    : `Climbing to ${settings.rampTo} over ${settings.rampMinutes} min`,
);

function onTarget(event: Event) {
  update({ rampTo: Number((event.target as HTMLInputElement).value) });
}

function onMinutes(event: Event) {
  update({ rampMinutes: Number((event.target as HTMLInputElement).value) });
}
</script>

<template>
  <section class="panel" aria-labelledby="rampLabel">
    <h2 id="rampLabel">Cadence ramp</h2>
    <p class="hint">
      Start where you run now and climb to the target across the run. Adding a
      few steps per minute at a time sticks better than jumping to the number.
    </p>
    <div class="switches">
      <BaseToggleSwitch
        label="Ramp the cadence"
        hint="Climb from where you start to the target"
        :checked="settings.rampOn"
        @change="setRampOn"
      />
    </div>
    <div class="ramp-fields" :data-enabled="String(settings.rampOn)">
      <div class="row">
        <label for="rampTo">Target</label>
        <input
          id="rampTo"
          type="range"
          :min="MIN_BPM"
          :max="MAX_BPM"
          step="1"
          :value="settings.rampTo"
          aria-label="Ramp target cadence"
          @input="onTarget"
        >
        <output class="value" for="rampTo">{{ settings.rampTo }}</output>
      </div>
      <div class="row">
        <label for="rampMinutes">Over</label>
        <input
          id="rampMinutes"
          type="range"
          :min="MIN_RAMP_MINUTES"
          :max="MAX_RAMP_MINUTES"
          step="1"
          :value="settings.rampMinutes"
          aria-label="Ramp length in minutes"
          @input="onMinutes"
        >
        <output class="value" for="rampMinutes">{{ settings.rampMinutes }} min</output>
      </div>
      <div class="ramp-progress" :hidden="!(settings.rampOn && running)">
        <div class="track"><div class="fill" :style="{ transform: `scaleX(${progress})` }"></div></div>
        <p class="hint">{{ status }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ramp-fields {
  margin-top: var(--space-4);
  transition: opacity var(--base) var(--ease);
}

.ramp-fields[data-enabled='false'] {
  opacity: 0.55;
  pointer-events: none;
}

.ramp-progress {
  margin-top: var(--space-4);
}

.track {
  height: 0.375rem;
  border-radius: var(--radius-pill);
  background: var(--raise);
  overflow: hidden;
}

/* Scaled rather than resized: animating width would relayout on every tick. */
.fill {
  height: 100%;
  width: 100%;
  border-radius: inherit;
  background: var(--accent);
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform 1s linear;
}
</style>
