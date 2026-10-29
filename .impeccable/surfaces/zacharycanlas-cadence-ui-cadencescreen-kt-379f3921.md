---
version: 1
slug: "zacharycanlas-cadence-ui-cadencescreen-kt-379f3921"
primary_target: "app/src/main/kotlin/io/github/zacharycanlas/cadence/ui/CadenceScreen.kt"
related_targets: []
---

# Main screen

Scope: the single scrolling run screen, its states, the run notification and the app icon. Mode: Operate.

Audience and job: recreational runners set cadence, sound, accent and ramp standing still, tap Start, then glance at arm's length mid-run in daylight or dark. Launch to beat in two taps. Constraints: Material 3 components, 48 dp targets, edge-to-edge insets, portrait, font scale to 2.0, animations-off safe, English strings in strings.xml.

## Direction contract

THESIS: The run screen is the runner's pace band: one cobalt band holds the cadence, and the ramp prints as split rows you read like splits. It refuses the category default of a centred number in a progress ring on a tinted surface.

OWN-WORLD: White lamination ground, ink #0E1116, cobalt #1F4FD8 band owning the hero, hi-vis lime #C8F031 only for "you are here" (the current ramp row, the beat flash). Ruled hairline modules under small uppercase header tabs, packed tight. Condensed tabular figures for every measurement, Roboto for the rest. Dark: ink ground, deeper cobalt band, same lime.

STORY: The runner sees their cadence first, sets it and the rest in one scroll, taps Start, and mid-run reads cadence, clock, steps and where the ramp has reached.

FIRST VIEWPORT: An edge-to-edge cobalt band, under the status bar, holds the beat ring and 172 in huge white tabular figures, "steps per minute", then clock · steps. Below on white: −, slider, +; Tap your stride; presets. Start/Stop docked in a bottom bar above the navigation inset. Signature interaction: the ramp's split rows, the current row lit lime and advancing as the run climbs; the beat lights the band's ring in lime, held 120 ms with no movement when animations are off.

FORM: Pace Band, candidate 3 of 7 in resonance order, seed key c98130fb.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
