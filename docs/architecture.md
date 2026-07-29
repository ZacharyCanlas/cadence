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
  cadence.js        the cadence chosen vs the cadence sounding
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

**Pure logic, no DOM and no audio.** `beat-grid.js`, `ramp.js`, `tap-tempo.js`,
`cadence.js` and the validation half of `settings.js`. This is the code that is easy to get
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
  unwinds committed audio instead of waiting for it to drain. Revoking is
  announced through `onRevoke`, because the re-planned beats re-use the numbers
  that were just thrown away.

Four invariants are worth stating because they are easy to break:

1. **Accent phase is tracked separately from grid index.** The grid re-anchors
   on a tempo change; the accent pattern must carry straight through. Revoking
   beats hands the beat numbering back to where it was for this reason.
2. **A re-used beat number is always announced first.** Anything holding beats
   it has not yet drawn or counted cannot otherwise tell the second beat 41 from
   the first, and will treat both as having sounded.
3. **Anything derived from time is computed from elapsed, not accumulated.**
   The session clock and the ramp both follow this, so a throttled timer in the
   background changes nothing about where they end up.
4. **The cadence you chose and the cadence sounding are different numbers.**
   Only the first is ever saved. Persisting the second let a ramp overwrite its
   own starting point, so it worked exactly once. `cadence.js` holds the split.

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

The scheduler is covered too, behind the fake clock in
`test/helpers/audio-harness.js`. This document used to argue that mocking the
audio context was not worth the effort. That was wrong, and cheaply so: the fake
is about two hundred lines with no dependencies, and the hard part turned out to
be only that `advance()` has to step `context.currentTime` and the timers
together in small slices, so a vibration timer never fires at a moment when the
audio clock disagrees about what time it is. Node's own `mock.timers` does the
rest.

What has not changed is that this says nothing about what a real mobile OS does
to a backgrounded page. That is still verified by hand on a device using the
checklist in
[background-audio.md](background-audio.md#verifying-on-a-device). The fake
answers a narrower question — whether the scheduler does what it means to — and
that question turned out to be where the bugs were.

The DOM is still left out, and `main.js` with it, since it calls `init()` at
import. That is still the largest untested surface in the app. The way to shrink
it is the way `cadence.js` was arrived at: when a defect turns out to be
untestable, move the rule it broke into a module with no DOM in it, rather than
fixing it in place and hoping.

Writing tests keeps finding defects in code that looked obviously correct. The
pure modules gave up two: `beatIndexAt` returned `-0` at the grid origin, which
would have propagated into beat numbering and accent arithmetic, and
`beatsElapsed` was off by one exactly on the first beat. The scheduler gave up
three more, all of which bite hardest with the screen off, which is precisely
where hand-testing is least reliable:

- Vibration stopped about a second after the page was hidden, because the whole
  six-second lookahead sits past the one-second horizon a haptic timer can be
  armed within, and nothing re-armed it.
- A tempo change re-used the beat numbers it had just revoked, so the step
  counter counted its own lookahead twice — better than double while hidden.
- A tempo drop could place the first beat of the new grid a few milliseconds
  after a beat that had survived inside the change lead. A sweep of phase
  against tempo delta found 180 to 164 landing two beats 32ms apart where the
  shortest legal gap is 333ms.
- The mirror of that, found by reviewing the fix rather than by running it: a
  tempo *rise* could round past a slot sitting inside the change lead and drop
  the beat entirely, leaving a silence of two periods. About one change in six
  for a jump like 150 to 180. This one predates the fix above and was not
  introduced by it.

A test suite that runs green says nothing on its own about whether it would go
red. Each of these is pinned by a test that has been checked against a
deliberately broken copy of the code it covers, and several of the first
attempts turned out to pass against the mutation — `stopping unwinds what was
already committed` proved the pump had stopped, which it would have done with
the unwinding removed entirely. Worth repeating that check when adding to these
files: write the mutation, confirm the red, then revert.

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
