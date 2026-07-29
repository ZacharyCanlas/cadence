/**
 * The persisted settings as one reactive object, shared by every component.
 *
 * Validation and storage live in core/settings.ts and are unchanged; this is
 * only the bridge into Vue's reactivity. Nothing here writes to the engine:
 * the actions in useMetronome do that, so the ordering rules (engine first,
 * then persist what it actually applied) stay in one place.
 */

import { reactive } from 'vue';
import { load, safeLocalStorage, save, type Settings } from '../core/settings.ts';

const storage = safeLocalStorage();
const settings = reactive<Settings>(load(storage));

function update(patch: Partial<Settings>): void {
  Object.assign(settings, patch);
  save(storage, settings);
}

export function useSettings() {
  return { settings, update };
}
