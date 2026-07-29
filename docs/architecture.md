# Architecture

How the app is put together, and the reasoning behind the structural choices.

## Layout

```
index.html               Vite entry; head only, Vue owns the body
vite.config.ts           Vue plugin + PWA manifest and Workbox config
src/
  main.ts                createApp, styles, service worker registration
  App.vue                the one screen: hero, panels, dock
  styles.css             design tokens and every component (unchanged by Vue)
  core/                  pure logic; no DOM, no audio, no Vue
    beat-grid.ts         pure beat maths (anchor + n * period)
    cadence.ts           the cadence chosen vs the cadence sounding
    ramp.ts              pure cadence-ramp maths
    tap-tempo.ts         pure rolling-average tap detection
    settings.ts          validated, persisted settings
    sounds.ts            the eight synthesised voices
    presets.ts           training presets and accent patterns
    format.ts            clock and step-count formatting
  platform/              thin wrappers around browser APIs; no Vue either
    audio-engine.ts      the scheduler; queues revocable beats on the audio clock
    platform.ts          feature detection and the Audio Session wrapper
    keep-alive.ts        the media element that keeps the page resident
    media-session.ts     lock screen controls
    wake-lock.ts         screen wake lock
    pump.ts              worker-backed scheduler heartbeat
  composables/
    useSettings.ts       the settings object made reactive, persistence on write
    useMetronome.ts      the one engine instance, mirrored into refs; all actions
  components/
    BeatRing.vue         the pulsing ring, beat markers, big number
    TempoControl.vue     minus / slider / plus
    TapTempoButton.vue   tap-your-stride reader
    SegmentedGroup.vue   accessible radio row (presets, voices, accents)
    ToggleSwitch.vue     accessible switch row
    *Panel.vue           one component per panel of the setup surface
public/                  icons and the legacy service worker cleanup script
test/                    node --test, still zero dependencies (type stripping)
```

## The organising principle

Modules fall into three groups, and the split is what makes the app testable.

**Pure logic, no DOM and no audio.** Everything in `src/core/`. This is the
code that is easy to get quietly wrong and hard to notice on a run: an
off-by-one in beat numbering or a ramp that overshoots its target will not
throw an error, it will just be subtly incorrect for forty minutes. All of it
is unit tested, and none of it imports Vue.

**Thin wrappers around browser APIs.** Everything in `src/platform/`. Each is
small enough to read in one sitting and does nothing clever, because the
browser is already doing the complicated part. These are verified in a real
browser, not mocked. `audio-engine.ts` sits between the two groups and is
tested against a fake clock.

**The Vue layer.** `useMetronome.ts` is the old composition root reborn: it
owns the single engine instance, mirrors the state templates need into refs,
and holds every invariant the controls rely on (engine first, then persist
what it actually applied; ramp output never saved; the keep-alive element
started inside the user gesture). Components stay declarative and dumb; the
one deliberate exception is the ring flash in `BeatRing.vue`, which stays
imperative because a class binding cannot restart a CSS animation reliably.

## The framework decision

Earlier revisions of this app were deliberately build-free vanilla JS, and
this document argued for that while the app was one screen driven by hand-wired
DOM updates. The rework to Vue 3 replaced the hand synchronisation (`paint*`
functions and manual `setValue` calls) with declarative templates, at the cost
the old argument predicted: a build step, a dependency tree to keep patched,
and CI for GitHub Pages.

What the migration deliberately did *not* touch is the part of the app that is
actually hard: `core/` and `platform/` are the same modules the vanilla app
ran, none of them import Vue, and the test suite that pins the scheduler ran
unchanged against them throughout. If Vue ever needs to go, it takes
`components/` and `composables/` with it and nothing else.

Conventions the Vue layer follows: `<script setup lang="ts">` single-file
components, strict TypeScript everywhere (`vue-tsc` gates the build), state
shared through composable singletons rather than a store library (Pinia earns
its place when there are multiple stores or devtools needs; one screen with
one engine does not need it), props down and events up, and reactive state
mirrored out of the engine rather than the engine made reactive.

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
   own starting point, so it worked exactly once. `core/cadence.ts` holds the
   split.

## Sound synthesis

The eight voices in `core/sounds.ts` are built from oscillators and a shared noise
buffer rather than loaded from files. This keeps the app a few kilobytes and
working offline from first install, and it means the accent can be a re-pitched
variant of the same voice rather than a second recording.

Each voice exposes `play(context, destination, time, options)` and schedules
itself at `time`. Nothing in the module reads the current clock, which is what
allows a voice to be queued seconds ahead without sounding different.

## Testing

`npm test` runs Node's built-in test runner directly against the TypeScript
sources — Node 22.18+ strips the types natively, so the suite still has no
dependencies and no config. `npm run typecheck` (also the first half of
`npm run build`) runs `vue-tsc` over sources, components and tests.

Coverage targets the pure modules: beat times not drifting over an hour, a
stall skipping missed beats rather than bursting, the tap average settling and
ageing out old taps, the ramp curve hitting its target from both directions,
and settings surviving corrupt, out-of-range and hostile storage.

The scheduler is covered too, behind the fake clock in
`test/helpers/audio-harness.ts`. This document used to argue that mocking the
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

The DOM is still left out, and the Vue layer with it: `useMetronome.ts` and
the components are the largest untested surface in the app. The way to shrink
it is the way `core/cadence.ts` was arrived at: when a defect turns out to be
untestable, move the rule it broke into a module with no DOM in it, rather
than fixing it in place and hoping. (Component tests via Vitest and
@vue/test-utils are the alternative if that ever stops being enough.)

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

The offline shell is generated by `vite-plugin-pwa` (Workbox `generateSW`)
rather than written by hand: the whole build output is precached, hashed
filenames make cache invalidation exact, and `registerType: 'autoUpdate'`
swaps a new deploy in on the next launch with a connection. This changes the
navigation strategy from the old worker's network-first to offline-first,
which is the right trade for an app whose whole point is working mid-run with
no signal.

`public/sw-legacy-cleanup.js` rides along inside the generated worker to
delete the `cadence-v*` caches the hand-written pre-Vite worker left behind;
Workbox only cleans caches it owns.

Because the shipped files are now build artifacts, GitHub Pages deploys
through the workflow in `.github/workflows/deploy.yml` instead of serving the
repository directly.

## Design system

Tokens live at the top of `src/styles.css`. The layout treats the screen as two
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
