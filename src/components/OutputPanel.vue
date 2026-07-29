<script setup lang="ts">
import { computed } from 'vue';
import { useMetronome } from '../composables/useMetronome.ts';
import { useSettings } from '../composables/useSettings.ts';
import { capabilities, isIOS } from '../platform/platform.ts';
import BaseToggleSwitch from './BaseToggleSwitch.vue';

const { settings } = useSettings();
const { setClick, setVibrate, setBackground } = useMetronome();

// Whichever output is the last one standing gets locked on. Disabling it
// says so up front, rather than letting the tap land and quietly do nothing.
const onlyClick = computed(() => settings.click && !settings.vibrate);
const onlyVibrate = computed(() => settings.vibrate && !settings.click);

const vibrateHint = capabilities.vibrate
  ? 'A pulse per step, silent to everyone else'
  : 'This browser does not offer vibration';

const backgroundHint = computed(() => {
  if (settings.background) {
    return isIOS()
      ? 'Carries on with the screen off. On iPhone this takes sole use of the speaker, so other players pause.'
      : 'Carries on with the screen off and through an app switch.';
  }
  return isIOS()
    ? 'Shares the speaker so your music keeps playing, but the beat stops once the screen locks.'
    : 'Gives your music priority. The beat may stop when the app is hidden.';
});
</script>

<template>
  <section class="panel" aria-labelledby="outputLabel">
    <h2 id="outputLabel">Output</h2>
    <div class="switches">
      <BaseToggleSwitch
        label="Click"
        hint="The sound you picked above"
        :checked="settings.click"
        :disabled="onlyClick"
        @change="setClick"
      />
      <BaseToggleSwitch
        label="Vibrate"
        :hint="vibrateHint"
        :checked="settings.vibrate && capabilities.vibrate"
        :disabled="!capabilities.vibrate || onlyVibrate"
        @change="setVibrate"
      />
      <BaseToggleSwitch
        label="Keep playing in the background"
        :hint="backgroundHint"
        :checked="settings.background"
        @change="setBackground"
      />
    </div>
    <p class="hint">One output always stays on, so there is never a run with nothing to follow.</p>
  </section>
</template>
