# Findings

Observations from reviewing the source that the test suite cannot settle on its
own. Anything provable gets a test instead; this file is for what is left.

## Verified by inspection only

**`main.js` cannot be loaded under `node --test`.** It calls `init()` at import
and reaches for `document` throughout, so the composition root has no automated
coverage at all. The engine side of the beat feed is tested through
`test/helpers/audio-harness.js`, but the half that lives in `main.js` — the
`onRevoke` handler that drops stale pending beats, the animation-loop drain, the
session clock — is checked by reading it.

Worth revisiting if the file grows. Splitting the beat bookkeeping
(`pendingBeats`, `stepCount`, `drainPast`) into a module with no DOM in it would
make the step total testable without a browser, and that is the part with real
logic in it.

## Defects that need a structural change before they can be tested

**The cadence ramp works once, then silently does nothing.** `tickSession`
applies each ramp step through `setBpm`, which calls `update({ bpm })`, which
persists. By the end of a run `settings.bpm` holds the ramp's target rather than
the cadence you actually started at, and that is what is saved.

Next run, `start()` sets `rampFrom = settings.bpm`, so the ramp climbs from its
own target to its own target. `rampBpmAt(180, 180, …)` is a flat line: no ramp,
no warning, and the starting cadence you had chosen is gone for good.

A ramp of 150 to 180 leaves you starting every subsequent run at 180 — the
overstriding the feature exists to train you out of. It is the one defect found
so far that changes what the app does to a runner rather than how well it does
it.

The fix is small in itself: keep ramp-driven cadence changes out of what gets
persisted as the starting cadence. It is untestable where it sits, because the
whole state machine — `rampFrom`, `settings.bpm`, the persistence — lives in
`main.js` behind `document`. Worth pulling the cadence bookkeeping into its own
module first, so the fix arrives with a test rather than with an argument.

## Open questions, not defects

**A manual cadence change mid-ramp restarts the climb from the new tempo, but
the clock keeps running.** `tickSession` derives the target from
`rampBpmAt(rampFrom, …, elapsed)`, and a manual `setBpm` resets `rampFrom`
without resetting `elapsed`. Twenty minutes into a thirty-minute ramp, nudging
the cadence down one step makes the next tick jump most of the way back up,
because two thirds of the ramp's span is now applied to the new starting point.

Arguably correct — you asked for a ramp to a target and the target has not
moved. Arguably wrong — the nudge is immediately undone. Needs a decision about
what the control is for before it is worth changing.

**Volume is applied as a direct gain assignment.** `setVolume` sets
`master.gain.value` outright rather than ramping, so dragging the slider during
a run steps the gain rather than sliding it. Whether that is audible on a phone
speaker under music is not something a unit test can answer; it needs an ear on
a device.

## Confirmed on a device

Nothing yet. Findings that need the device checklist in
[background-audio.md](background-audio.md#verifying-on-a-device) belong here once
they have been through it.
