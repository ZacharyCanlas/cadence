<script setup lang="ts">
import { onMounted, onUnmounted, ref, useTemplateRef, watch } from 'vue';
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

const ring = useTemplateRef('ring');
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

onUnmounted(() => unsubscribe?.());

// Watchers, not computeds, because both react to an external change by
// resetting imperative state: the flash classes live outside Vue's rendering
// on purpose, and the lit dot must go dark rather than be re-derived.
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

<style scoped>
.ring {
  position: relative;
  display: grid;
  place-items: center;
  width: min(64vw, 15rem);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 2px solid var(--line-strong);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  transition:
    border-color var(--base) var(--ease),
    transform var(--base) var(--ease),
    box-shadow var(--base) var(--ease);
}

/* The one authored moment: the ring answers every beat. Snap in, settle out. */
.ring.tick {
  border-color: var(--accent);
  transform: scale(1.025);
  box-shadow: var(--shadow-1), 0 0 0 5px var(--accent-wash);
  transition: none;
}

.ring.tick.accent {
  transform: scale(1.05);
  box-shadow: var(--shadow-1), 0 0 0 9px var(--accent-wash);
}

.readout {
  display: grid;
  justify-items: center;
  gap: var(--space-1);
}

.bpm {
  font-family: var(--mono);
  font-size: 4.25rem;
  font-weight: 700;
  line-height: 0.95;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.unit {
  font-size: var(--text-xs);
  font-weight: 550;
  letter-spacing: 0.06em;
  color: var(--ink-2);
}

/* Accent markers sit on the ring so you can see where in the cycle you are. */
.beats {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.beat-dot {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: start center;
}

.beat-dot::before {
  content: '';
  width: 0.3rem;
  height: 0.3rem;
  margin-top: -0.15rem;
  border-radius: 50%;
  background: var(--line-strong);
  transition: background var(--fast) var(--ease), transform var(--fast) var(--ease);
}

.beat-dot[data-on='true']::before {
  background: var(--accent);
  transform: scale(1.6);
}

.beat-dot[data-accent='true']::before {
  width: 0.45rem;
  height: 0.45rem;
  margin-top: -0.225rem;
}

@media (min-width: 30rem) {
  .bpm {
    font-size: 5rem;
  }

  .ring {
    width: 16rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  /* The beat still has to be visible, so it stays as a colour change with no
     movement rather than being removed. */
  .ring.tick,
  .ring.tick.accent {
    transform: none;
    box-shadow: var(--shadow-1);
    border-color: var(--accent);
  }

  .beat-dot[data-on='true']::before {
    transform: none;
  }
}
</style>
