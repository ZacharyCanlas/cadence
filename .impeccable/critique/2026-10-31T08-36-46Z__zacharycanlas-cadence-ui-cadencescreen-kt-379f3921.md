---
target: the whole run screen
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/root/development/cadence/app/src/main/kotlin/io/github/zacharycanlas/cadence/ui/CadenceScreen.kt"
target_fingerprint: "sha256:647c296b5bc73fe5f4f915a6ac61f2bbaf18650a1cf4517c5a5361485b06005f"
target_path: /root/development/cadence/app/src/main/kotlin/io/github/zacharycanlas/cadence/ui/CadenceScreen.kt
timestamp: 2026-10-31T08-36-46Z
slug: zacharycanlas-cadence-ui-cadencescreen-kt-379f3921
---
Method: dual-agent (A: design review · B: detector)

## Section verdicts
| Section | Verdict | Reason |
|---|---|---|
| Band | Keep | Carries the screen; stopped state needs a ramp preview and a labelled last-run clock |
| Cadence | Rework | Absorbs Presets and the Ramp; Tap becomes a secondary row with corrected copy |
| Presets | Merge into Cadence | Named cadence values, not a separate concern; drop "Set by hand" hint |
| Sound | Keep, absorb Output | Gains the beat-sound switch, owns volume, gains Vibrate |
| Accent | Keep | Five explained options; applies to vibration too |
| Output | Remove | Click and Vibrate move to Sound; notifications warning moves to top; output_hint goes |
| Ramp | Merge into Cadence, moved up | Its start value already lives in Cadence; hide Target/Over when off; drop duplicate status |
| Dock | Keep | Thumb-reachable, clear Start/Stop inversion |

## Heuristics: 26/40 (Acceptable)
1 Status 3 · 2 Real world 3 · 3 Control 2 · 4 Consistency 2 · 5 Error prevention 2 · 6 Recognition 3 · 7 Flexibility 3 · 8 Minimalist 2 · 9 Recovery 3 · 10 Help 3

## Priority issues
- P1 Ramp split from its start value and placed last (CadenceScreen.kt:170-175).
- P1 Mid-run cadence touches end the ramp with no visible way back; keepScreenOn makes pocket touches likely.
- P2 Output duplicates Sound: two "Click" controls, volume disabled from another module, notifications warning below the fold.
- P2 Copy: tap_hint "as you run" vs setup standing still; preset_none_hint shown mid-ramp; preset "Target" collides with ramp Target; ramp_hint/ramp_switch_hint duplicate; ramp status shown twice while running.
- P3 Stopped state: no ramp preview on band, unlabelled last-run clock, "Climbing" for a descending ramp.

## Detector
impeccable detect has no Kotlin/Compose rules: 0 findings, no coverage. Android Lint passes.
