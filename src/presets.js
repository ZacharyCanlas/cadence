/**
 * Training presets and accent patterns.
 *
 * The cadence numbers follow the usual coaching advice: most runners land
 * somewhere in the 160s, 180 is the commonly quoted target, and the way to get
 * there is to add roughly five steps per minute at a time rather than jumping
 * straight to the number. The labels matter as much as the values, because
 * "Easy run" tells you when to use it and "170" does not.
 */

export const PRESETS = [
  { bpm: 165, label: 'Recovery', hint: 'Easy legs, low impact' },
  { bpm: 170, label: 'Easy', hint: 'Conversational pace' },
  { bpm: 175, label: 'Steady', hint: 'Most of your weekly mileage' },
  { bpm: 180, label: 'Target', hint: 'The cadence most coaching aims at' },
  { bpm: 185, label: 'Tempo', hint: 'Threshold and race effort' },
  { bpm: 190, label: 'Strides', hint: 'Short, fast turnover' },
];

/**
 * How often the accent sound lands. The three and six counts exist because
 * runners commonly pair breathing to stride: in for three steps, out for
 * three. The two count marks the same foot every time, which is how you feel
 * whether your stride is even.
 */
export const ACCENTS = [
  { every: 0, label: 'Off', hint: 'Every step sounds the same' },
  { every: 2, label: 'Every 2', hint: 'Marks the same foot each time' },
  { every: 3, label: 'Every 3', hint: 'Three-step breathing rhythm' },
  { every: 4, label: 'Every 4', hint: 'Four-count, alternating feet' },
  { every: 6, label: 'Every 6', hint: 'In for three, out for three' },
];

export const ACCENT_VALUES = ACCENTS.map((accent) => accent.every);

export function normalizeAccent(value) {
  const n = Math.round(Number(value));
  return ACCENT_VALUES.includes(n) ? n : 0;
}
