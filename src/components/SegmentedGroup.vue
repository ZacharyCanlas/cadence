<script lang="ts">
/** One choice in a segmented radio row. */
export interface SegmentedItem {
  value: string | number;
  label: string;
  sub?: string;
}
</script>

<script setup lang="ts">
import { ref } from 'vue';

/**
 * A row of mutually exclusive options, keyboard navigable with the arrow keys
 * the way a native radio group is. The container class and aria-labelledby
 * fall through from the caller, so each panel keeps its own styling hooks.
 */

const props = defineProps<{
  items: readonly SegmentedItem[];
  modelValue: string | number;
}>();

const emit = defineEmits<{ select: [value: string | number] }>();

const buttons = ref<(HTMLButtonElement | null)[]>([]);

function onKeydown(event: KeyboardEvent) {
  const offset = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
  if (!offset) return;
  const current = props.items.findIndex((item) => item.value === props.modelValue);
  const next = (current + offset + props.items.length) % props.items.length;
  event.preventDefault();
  buttons.value[next]?.focus();
  emit('select', props.items[next].value);
}
</script>

<template>
  <div role="radiogroup" @keydown="onKeydown">
    <button
      v-for="(item, index) in items"
      :key="item.value"
      :ref="(el) => (buttons[index] = el as HTMLButtonElement | null)"
      type="button"
      class="option"
      role="radio"
      :aria-checked="item.value === modelValue"
      :tabindex="item.value === modelValue ? 0 : -1"
      :data-value="String(item.value)"
      @click="emit('select', item.value)"
    >
      <span class="option-label">{{ item.label }}</span>
      <span v-if="item.sub" class="option-sub">{{ item.sub }}</span>
    </button>
  </div>
</template>
