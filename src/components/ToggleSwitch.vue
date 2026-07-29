<script setup lang="ts">
/**
 * An on/off control. Uses a button with `role="switch"` rather than a checkbox
 * so the whole row is one large tap target.
 */

defineProps<{
  label: string;
  hint?: string;
  checked: boolean;
  disabled?: boolean;
}>();

const emit = defineEmits<{ change: [value: boolean] }>();
</script>

<template>
  <button
    type="button"
    class="switch"
    role="switch"
    :aria-checked="checked"
    :disabled="disabled"
    @click="emit('change', !checked)"
  >
    <span class="switch-text">
      <span class="switch-label">{{ label }}</span>
      <span v-if="hint" class="switch-hint">{{ hint }}</span>
    </span>
    <span class="switch-state">{{ checked ? 'On' : 'Off' }}</span>
  </button>
</template>
