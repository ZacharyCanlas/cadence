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

<style scoped>
.switch {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: var(--tap);
  padding: var(--space-3);
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: var(--surface);
  text-align: left;
  transition: border-color var(--fast) var(--ease), background var(--fast) var(--ease);
}

.switch:hover:not(:disabled) {
  border-color: var(--line-strong);
}

.switch[aria-checked='true'] {
  border-color: var(--accent);
  background: var(--accent-wash);
}

.switch:disabled {
  opacity: 0.5;
}

.switch-text {
  display: grid;
  gap: 0.1rem;
  flex: 1;
  min-width: 0;
}

.switch-label {
  font-weight: 600;
}

.switch-hint {
  font-size: var(--text-sm);
  line-height: 1.45;
  color: var(--ink-2);
}

.switch-state {
  flex: 0 0 auto;
  align-self: start;
  padding: 0.15rem var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--raise);
  font-size: var(--text-xs);
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ink-2);
}

.switch[aria-checked='true'] .switch-state {
  background: var(--accent);
  color: var(--accent-ink);
}
</style>
