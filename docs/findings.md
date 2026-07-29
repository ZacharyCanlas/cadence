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

## Fixed

**The cadence ramp worked once, then silently did nothing.** Every ramp step was
persisted as the chosen cadence, so a run that climbed 150 to 180 saved 180. The
next run climbed from 180 to 180 — a flat line, no warning, and the cadence you
actually run at gone for good, leaving you starting every later run at the
overstriding number the feature exists to train you out of.

One value was doing two jobs. `cadence.js` splits them: `chosen` is yours and is
the only part saved, `current` is what is sounding and the ramp is free to move
it. Covered in `test/cadence.test.js`, including the cross-session round trip
through storage that is the actual defect.

What is left in `main.js` is one expression — `update({ bpm: cadence.chosen })`
— rather than a state machine spread across the file. Still not reachable from
`node --test`, but small enough to read in one go, and the rule it has to obey
is now enforced by a module that is tested.

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
