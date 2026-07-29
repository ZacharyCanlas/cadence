# Architecture

How the app is put together, and the reasoning behind the structural choices.

## Layout

```
index.html          structure only
styles.css          design tokens and every component
src/
  main.js           composition root: builds controls, wires them to the engine
  audio-engine.js   the scheduler; queues revocable beats on the audio clock
  beat-grid.js      pure beat maths (anchor + n * period)
  sounds.js         the eight synthesised voices
  presets.js        training presets and accent patterns
  ramp.js           pure cadence-ramp maths
  tap-tempo.js      pure rolling-average tap detection
  settings.js       validated, persisted settings
  platform.js       feature detection and the Audio Session wrapper
  keep-alive.js     the media element that keeps the page resident
  media-session.js  lock screen controls
  wake-lock.js      screen wake lock
  pump.js           worker-backed scheduler heartbeat
  ui.js             small DOM builders
sw.js               offline shell
test/               node --test, no dependencies
```

## The organising principle

Modules fall into two groups, and the split is what makes the app testable.

**Pure logic, no DOM and no audio.** `beat-grid.js`, `ramp.js`, `tap-tempo.js`
and the validation half of `settings.js`. This is the code that is easy to get
quietly wrong and hard to notice on a run: an off-by-one in beat numbering or a
ramp that overshoots its target will not throw an error, it will just be
subtly incorrect for forty minutes. All of it is unit tested.

**Thin wrappers around browser APIs.** `platform.js`, `keep-alive.js`,
`media-session.js`, `wake-lock.js`, `pump.js`. Each is small enough to read in
one sitting and does nothing clever, because the browser is already doing the
complicated part. These are verified in a real browser, not mocked.

`audio-engine.js` sits between the two, and `main.js` is the only place that
knows about both the DOM and the engine.

## No framework

The app is one screen with roughly a dozen controls, no routing, no server
state and no shared component tree. React or Svelte would add a build step, a
dependency tree to keep patched, and a deploy pipeline, in exchange for solving
problems this app does not have.

The real structural problem was a single 660 line `index.html` holding markup,
styles and all logic at once. ES modules solved that without adding a
toolchain. Staying build-free has two further benefits worth keeping: the
source that ships is the source you read, and GitHub Pages needs no CI.

Worth revisiting if the app grows a second screen, run history, or accounts.

## The scheduling model

Covered in depth in [background-audio.md](background-audio.md). In short:

- Beats are placed on the audio hardware clock ahead of time, never fired by a
  timer, so timer jitter never reaches the ear.
- Beat times are derived from an anchor (`anchor + index * period`) rather than
  accumulated, so float error cannot pile up over a long run.
- The lookahead window is 0.25s while visible and 6s while hidden.
- A worker drives the heartbeat, keeping it clear of main-thread jank and of
  the throttling applied to pages that are not in front.
- After a stall longer than the tolerance, the grid skips to the next live slot
  rather than firing every missed beat at once.
- Everything queued is revocable, so a tempo change, a new sound or a stop
  unwinds committed audio instead of waiting for it to drain.

Two invariants are worth stating because they are easy to break:

1. **Accent phase is tracked separately from grid index.** The grid re-anchors
   on a tempo change; the accent pattern must carry straight through. Revoking
   beats hands the beat numbering back to where it was for this reason.
2. **Anything derived from time is computed from elapsed, not accumulated.**
   The session clock and the ramp both follow this, so a throttled timer in the
   background changes nothing about where they end up.

## Sound synthesis

The eight voices in `sounds.js` are built from oscillators and a shared noise
buffer rather than loaded from files. This keeps the app a few kilobytes and
working offline from first install, and it means the accent can be a re-pitched
variant of the same voice rather than a second recording.

Each voice exposes `play(context, destination, time, options)` and schedules
itself at `time`. Nothing in the module reads the current clock, which is what
allows a voice to be queued seconds ahead without sounding different.

## Testing

`npm test` runs Node's built-in test runner. No dependencies, no config.

Coverage targets the pure modules: beat times not drifting over an hour, a
stall skipping missed beats rather than bursting, the tap average settling and
ageing out old taps, the ramp curve hitting its target from both directions,
and settings surviving corrupt, out-of-range and hostile storage.

The audio graph and the DOM are deliberately left out. An integration test for
them would need a headless browser and a mocked audio context, and would still
not answer the only question that matters for this app, which is what a real
mobile OS does to a backgrounded page. That is verified by hand on a device
using the checklist in [background-audio.md](background-audio.md#verifying-on-a-device).

Writing the tests found two real defects, both in code that looked obviously
correct: `beatIndexAt` returned `-0` at the grid origin, which would have
propagated into beat numbering and accent arithmetic, and `beatsElapsed` was
off by one exactly on the first beat.

## Service worker

Navigations go to the network first and fall back to the cached page, so a
deploy is picked up on the next launch with a connection. Everything else is
served from cache and refreshed in the background, so a run never waits on the
network.

The install step fetches shell entries individually rather than with `addAll`,
which is all-or-nothing and would leave the app with no cache at all if a
single path were wrong.

## Design system

Tokens live at the top of `styles.css`. The layout treats the screen as two
surfaces: the top is an instrument, read at arm's length in daylight while
moving, and gets the size and contrast; the bottom is setup, read standing
still, and gets the density.

Conventions worth preserving:

- Monospace is for measurements only (cadence, clock, counts), never for labels.
- Sections are separated by hairlines rather than boxed as cards.
- Minimum 44px on anything pressable.
- Light and dark are both first class; this is used at 6am and at 9pm.
- Reduced motion keeps the beat visible as a colour change rather than removing
  the feedback, since the pulse is functional rather than decorative.
