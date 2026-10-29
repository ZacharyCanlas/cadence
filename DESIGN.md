---
name: Cadence
description: A running metronome whose run screen reads like a printed pace band.
colors:
  cobalt: "#1F4FD8"
  cobalt-deep: "#16359A"
  cobalt-tint: "#9DB5FF"
  cobalt-container: "#DDE5FC"
  cobalt-ink: "#0B2370"
  lime: "#C8F031"
  ink: "#0E1116"
  laminate: "#FFFFFF"
  on-band-muted: "#C9D6FA"
  on-band-muted-dark: "#B4C4F2"
  rule: "#D5DAE3"
  rule-dark: "#2A303B"
  slate: "#3B4352"
  hint-grey: "#4A5261"
  hint-grey-dark: "#AEB5C2"
  wash: "#F1F3F6"
  wash-dark: "#1B2029"
  container: "#F4F6F9"
  container-dark: "#161A21"
  outline: "#7A8292"
  outline-dark: "#8A92A1"
  paper-dark: "#E9ECF1"
typography:
  display:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "128sp"
    fontWeight: 600
    lineHeight: "120sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "26sp"
    fontWeight: 500
    lineHeight: "30sp"
    fontFeature: "tnum"
  figure-body:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "20sp"
    fontWeight: 500
    lineHeight: "24sp"
    fontFeature: "tnum"
  title-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "28sp"
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "0.15sp"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0.25sp"
  body-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
  tab:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "13sp"
    fontWeight: 600
    lineHeight: "16sp"
    letterSpacing: "1.4sp"
rounded:
  xs: "4dp"
  md: "10dp"
  lg: "14dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  gutter: "20dp"
  xl: "24dp"
components:
  band:
    backgroundColor: "{colors.cobalt}"
    textColor: "{colors.laminate}"
    padding: "20dp 20dp 28dp"
  band-dark:
    backgroundColor: "{colors.cobalt-deep}"
    textColor: "{colors.laminate}"
  module-tab:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.laminate}"
    typography: "{typography.tab}"
    rounded: "{rounded.xs}"
    padding: "3dp 8dp"
  cell:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    padding: "8dp"
    height: "56dp"
  cell-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.laminate}"
  split-row:
    textColor: "{colors.ink}"
    typography: "{typography.figure-body}"
    padding: "8dp 16dp"
  split-row-lit:
    backgroundColor: "{colors.lime}"
    textColor: "{colors.ink}"
  button-start:
    backgroundColor: "{colors.cobalt}"
    textColor: "{colors.laminate}"
    typography: "{typography.title-large}"
    rounded: "{rounded.lg}"
    padding: "0 24dp"
    height: "60dp"
  button-stop:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.laminate}"
    typography: "{typography.title-large}"
    rounded: "{rounded.lg}"
    height: "60dp"
  button-tap:
    backgroundColor: "transparent"
    textColor: "{colors.cobalt}"
    typography: "{typography.title}"
    rounded: "{rounded.md}"
    height: "64dp"
  button-step:
    backgroundColor: "{colors.wash}"
    textColor: "{colors.ink}"
    size: "52dp"
---

# Design System: Cadence

## Overview

**Creative North Star: "The Pace Band"**

The run screen is a runner's pace band: a laminated strip with one cobalt band holding the cadence and the ramp printed as split rows you read like splits. Everything below the band is white lamination ruled into tight modules, each under a small ink header tab seated on its top rule, the way a printed band or race card divides its fields. Measurements are always condensed tabular figures; words are Roboto. Hi-vis lime appears only where the runner is right now.

Density is packed and tabular rather than airy: modules sit 16dp apart, options are ruled cells in a grid, and the one large figure is the cadence in the band. Dark mode is the same band at night: ink ground, a deeper cobalt band, the same lime. The world rejects the category default of a centred number inside a progress ring on a tinted surface; the band is edge-to-edge and the ring is a thin beat marker, not a progress meter.

**Key Characteristics:**
- One edge-to-edge cobalt band under the status bar owns the first viewport.
- Lime marks "you are here" and nothing else.
- Hairline-ruled modules under ink header tabs, packed tight on a 20dp gutter.
- Condensed tabular figures for every measurement; Roboto for every word.
- Selection is inked solid, never tinted.

## Colors

A laminated-paper palette: white and ink, one saturated cobalt, one hi-vis lime held in reserve.

### Primary
- **Band Cobalt** (cobalt): the band's ground in light mode, the launcher icon background, the Start button, slider active tracks and the Tap button's outline text. Primary across Material roles.
- **Night Cobalt** (cobalt-deep): the band's ground in dark mode and the dark primary container.
- **Cobalt Tint** (cobalt-tint): dark-mode primary, where Band Cobalt would sink into the ink ground.
- **Cobalt Wash / Cobalt Ink** (cobalt-container, cobalt-ink): light primary container and its text.

### Secondary
- **Hi-Vis Lime** (lime): the current split row, the lit beat marker on the ring, and the accent marker in the launcher icon. Text on lime is always Ink.

### Neutral
- **Laminate** (laminate): light ground and every text colour on the band.
- **Ink** (ink): light text, dark ground, module tabs and their 2dp top rules, selected cells, the running Stop button.
- **Band Muted** (on-band-muted, on-band-muted-dark): secondary text on the band ("steps per minute", ramp status), unlit beat dots, and at 45-50% alpha the ring and the band's split-strip rules. Tinted from the band, never grey.
- **Hairline** (rule, rule-dark): the 1dp rules of cell grids and split rows; also Material's outlineVariant.
- **Hint Grey** (hint-grey, hint-grey-dark): hint and explanatory body text under controls.
- **Wash / Container** (wash, container and their dark pairs): surfaceVariant and surfaceContainer; carries the tonal step buttons.
- **Slate** (slate): Material secondary. **Outline** (outline, outline-dark): Material outline, the Tap button stroke. **Paper Dark** (paper-dark): dark-mode text.

### Named Rules
**The You-Are-Here Rule.** Lime marks the runner's present position (the current ramp split, the beat flash) and nothing else. No lime buttons, borders, icons or decoration.

**The Band-Tinted Rule.** Secondary text and rules on the band are cobalt-tinted (Band Muted), never neutral grey or white at reduced alpha over grey.

## Typography

**Display Font:** Barlow Condensed (bundled, Medium 500 and SemiBold 600), tabular figures
**Body Font:** Roboto (system)

**Character:** Barlow Condensed is the printed band: narrow, upright figures that line up in columns. Roboto carries every sentence so instructions stay plain.

### Hierarchy
- **Display** (600, 128sp, 120sp line): the cadence figure on the band. One per screen.
- **Headline** (500, 26sp, 30sp line): the session line on the band, clock · steps.
- **Figure Body** (500, 20sp, 24sp line): figures inside running layout: split rows and the band's split strip, preset cadences, slider values. Split-row cadences step up to 600.
- **Title Large** (Roboto 400, 22sp): the Start/Stop label.
- **Title** (Roboto 600, 16sp): control labels: Tap your stride, switch rows, slider labels.
- **Body** (Roboto 400, 14sp): hints and status lines. **Body Small** (12sp): the tap hint inside the Tap button.
- **Label** (Roboto 500, 14sp): cell labels (preset names, sounds, accents).
- **Tab** (Barlow Condensed 600, 13sp, 1.4sp tracking, uppercase): module header tabs; also "steps per minute" under the cadence, in lowercase.

### Named Rules
**The Figures Rule.** Every number a runner reads (cadence, clock, steps, splits, preset values, slider values) is set in Barlow Condensed with `tnum`, so digits never jitter as they change. Words are never set in the figure face except the header tabs and the unit under the cadence.

## Layout

A single portrait scroll in Material 3's Scaffold, edge to edge. The band runs full-bleed under the status bar (status-bar inset applied inside the band) with 20dp top and 28dp bottom padding; the beat ring is a 272dp square centred in it. Below, modules stack on a 20dp gutter: 16dp above each tab, 8dp from rule to content, 12dp between controls inside a module, 8dp above hints, 24dp after the last module. Start/Stop docks in the Scaffold's bottom bar above the navigation inset, with 12dp vertical padding.

Touch targets are at least 48dp; in practice 52dp step buttons, 56dp cells, 64dp switch rows and Tap button, 60dp dock button. Layout responds to font scale, not width: cell grids drop to 3 columns at 1.25x and 2 at 1.7x, and value sliders stack label and value over a full-width track at 1.6x, so no label is clipped up to 2.0x.

### Named Rules
**The Scale-Not-Squeeze Rule.** When text grows, grids lose columns and rows stack; labels are never truncated or shrunk.

## Elevation & Depth

Flat and ruled. Depth comes from the band's colour block and from 1-2dp ink and hairline rules, not shadow. The one shadow is the bottom dock (Material tonal elevation 3dp, shadow elevation 6dp), which lifts Start/Stop over the scrolling modules.

### Named Rules
**The Ruled-Not-Raised Rule.** Modules, cells and split rows are separated by hairlines and ink rules; only the dock casts a shadow.

## Shapes

Small, near-square corners, like a die-cut laminate. Ruled containers (cell grids, split rows, the band's split strip) and module tabs use 4dp (tabs round only their top corners, sitting flat on the 2dp ink rule). The Tap button uses 10dp; the dock button 14dp. The beat ring and its accent dots are the only circles, and the launcher icon repeats them on Band Cobalt with the downbeat dot in lime.

## Components

### Buttons
- **Start (dock):** filled Band Cobalt, Laminate Title Large label, 14dp corners, full width, 60dp tall, 24dp horizontal padding.
- **Stop (dock, running):** the same button inverted to Ink fill and Laminate label (Paper Dark fill and Ink label in dark mode).
- **Tap your stride:** Material outlined button, 10dp corners, full width, 64dp min height; Title label over a Body Small hint that counts taps and reads the result.
- **Step (− / +):** Material filled tonal icon button, 52dp, flanking the cadence slider.
- **Text button:** plain Material text button, used only for the notification-settings link.

### Cell Grid
- **Style:** a 4dp-cornered box with a 1dp hairline border, ruled into rows and columns by 1dp hairlines; cells are 56dp minimum, 8dp padding, a Figure Body value over a Label.
- **State:** the selected cell is inked solid (Ink fill, Laminate text; inverted in dark). Radio semantics, one per group.

### Module
- **Header tab:** uppercase Tab text in Laminate on an Ink tab, 8dp by 3dp padding, top corners 4dp, seated on a 2dp Ink rule spanning the gutter. Announced as a heading.

### Switch rows and sliders
- **Switch row:** Title over Body hint, Material Switch at the end, the whole row toggleable, 64dp min height.
- **Value slider:** Title label, Material slider with tick marks removed, Figure Body value at the end; disabled rows fade to 38% alpha.

### Pace Band (signature)
- **Band:** cobalt block with the beat ring (3dp ring at Band Muted 45%, accent dots 7dp, downbeat dot 1.25x in Laminate), the Display cadence, "steps per minute", the Headline session line and a Body status line.
- **Beat flash:** the beat lights its dot, or the whole ring at double stroke when accents are off, in Lime for 120ms; no movement, so it reads the same with animations off.
- **Split rows:** the ramp printed as six rows (minute, cadence) inside a 4dp hairline box; the reached row fills Lime with Ink text. While climbing, the same splits print on the band as one 36dp strip of six cells.

### Run notification
- System-styled ongoing workout notification: monochrome beat-ring small icon, chronometer, and −1 / +1 / Stop actions with the same vector glyphs as the screen.

## Do's and Don'ts

### Do:
- **Do** set every measurement in Barlow Condensed with tabular figures.
- **Do** keep the cobalt band edge-to-edge under the status bar and give it the screen's only Display figure.
- **Do** start every new settings group with a module: an uppercase Ink header tab on a 2dp Ink rule, content on the 20dp gutter.
- **Do** show choices as ruled cells with the selected cell inked solid.
- **Do** keep the beat flash a colour change with no movement.

### Don't:
- **Don't** use lime for anything but the runner's current position and the beat flash.
- **Don't** put the cadence in a progress ring on a tinted card; the ring marks beats, never progress.
- **Don't** add shadows to modules, cells or rows; only the dock is lifted.
- **Don't** use neutral grey for secondary text on the band; use Band Muted.
- **Don't** truncate labels at large font scales; drop grid columns or stack instead.
