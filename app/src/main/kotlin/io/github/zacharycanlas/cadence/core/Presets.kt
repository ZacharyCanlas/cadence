package io.github.zacharycanlas.cadence.core

/** Training presets, in display order. Labels and hints live in strings.xml. */
val PRESET_BPMS: List<Int> = listOf(165, 170, 175, 180, 185, 190)

/** Beats between accents, in display order. 0 turns the accent off. */
val ACCENT_VALUES: List<Int> = listOf(0, 2, 3, 4, 6)

fun normalizeAccent(value: Int): Int = if (value in ACCENT_VALUES) value else 0
