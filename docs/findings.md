# Findings

Observations from reviewing the source that the test suite cannot settle on its
own. Anything provable gets a test instead; this file is for what is left.

The web app's findings, from the untested `main.js` to the iOS session-type
runs, ended with the web app's removal; git history keeps them.

## Open questions, not defects

**The volume slider steps the gain.** `MetronomeController.setVolume` passes
`value / 100f` to `TrackOutput.setVolume`, which calls `AudioTrack.setVolume`
outright. Cadence applies no ramp of its own, so dragging the slider mid-run
changes the beat's level in steps. Whether you can hear those steps through a
phone speaker under music takes an ear on a device; a unit test cannot answer
it.

## Resolved

**A manual cadence change mid-ramp holds.** The web app restarted the climb from
the new cadence and kept the ramp's clock running. Twenty minutes into a
thirty-minute ramp, a one-step nudge down came back undone at the next tick,
because two thirds of the ramp's span applied to the new starting point. The
notification's Slower and Faster put that nudge one tap from the lock screen,
so we settled the question: a deliberate change is never silently undone.

Every manual path (slider, ±1, preset, tap, and the notification's Slower and
Faster) goes through `MetronomeController.setCadence`. During a running ramp it
calls `Cadence.hold`, which sets the sounding cadence and `rampHeld` and leaves
`chosen`, `rampFrom` and storage alone. `rampTarget` then returns the held value
for the rest of the run. Outside a ramp, `setCadence` calls `choose` and saves.

- Stop runs `endRun`, which returns the display to `chosen`, and the next run
  climbs from it again.
- Changing the ramp's target or length keeps the hold. Turning the ramp off and
  on calls `rebaseRamp`, which clears the hold and climbs from where you are.
- The ramp panel and the notification read "Holding at 172. You took over from
  the ramp."

The hold leaves `chosen` alone on purpose. A −1 twenty minutes into a 150 to 180
ramp means "slower now", and saving 169 would bring back the defect where a ramp
saved its own output and so worked once. `CadenceTest` pins that run from start
to the next run's opening cadence, and `MetronomeControllerTest` pins both
branches of `setCadence`.

## Device checks

Results so far, against the
[device checklist](background-audio.md#verifying-on-a-device):

- **Passed:** the app looks right on the phone, and the beat plays over Spotify
  (step 1's basic case: Spotify first, then Cadence, both audible).
- **Open:** the rest of step 1 (Spotify's volume through a 10-minute run, Stop
  leaving Spotify playing, and the reverse order) and steps 2 to 16.

Two open checks carry a decision:

- **Step 3, underruns.** If the hour-long run's clock falls behind the phone's,
  or logcat reports a climbing count, `TrackOutput` moves to
  `PERFORMANCE_MODE_POWER_SAVING`, which the `AudioTrack` reference describes as
  "deeper internal buffers and better underrun resistance".
- **Step 7, the locked screen.** `setAuthenticationRequired(false)` asks for
  Slower, Faster and Stop to run without unlocking, and the OS decides. If the
  phone demands an unlock, the lock-screen ±1 needs another route.
