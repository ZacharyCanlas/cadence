# Cadence

A running metronome for the phone. It keeps time over your music, carries on
with the screen off, and installs to the home screen as a normal app.

## What it does

- **Cadence from 100 to 220 steps per minute**, by slider, by one-step buttons,
  by named preset, or by tapping along to your own stride.
- **Eight sounds**, all synthesised at runtime: click, woodblock, beep, cowbell,
  rimshot, kick drum, hi-hat, and a drum kit that puts a kick on the accent and
  a hi-hat between.
- **Accent every 2, 3, 4 or 6 steps.** Two marks the same foot each time, which
  is how you feel whether your stride is even. Three and six line the beat up
  with breathing, in for three steps and out for three.
- **Cadence ramp.** Start where you actually run and climb to a target across
  the run. Adding a few steps per minute at a time is what sticks; jumping
  straight to a number does not.
- **Vibration** instead of or alongside the sound, for treadmills and races.
- **Runs in the background**, so the beat survives an app switch and the screen
  locking.

## Background playback, and the honest limits

This is the hard part of the app, and it is worth being exact about what the
web can and cannot do.

**What works.** Beats are placed on the audio hardware clock ahead of time
rather than fired by a timer. While the app is in front the queue runs a quarter
of a second ahead, which keeps tempo changes instant. The moment the page is
hidden the queue extends to six seconds, so even a pump throttled to one tick a
second arrives with seconds of audio already committed to the hardware.
Alongside that, a near-silent looping media element holds an active media
session, which is what stops the browser suspending the page and what puts the
app on the lock screen. Together these carry the beat through an app switch and
a screen lock.

**What does not work, on any web app.** Fully closing the app stops the sound.
Background audio requires a process to stay alive, and a closed page has none.
Only a native app can do that, which is the one part of the original request the
platform genuinely cannot meet.

**Mixing with Spotify.** On iPhone this is a real either/or, not an oversight.
Safari's Audio Session API offers `ambient`, which shares the output so your
music keeps playing but is stopped by iOS the moment the app is backgrounded,
and `playback`, which survives backgrounding but takes sole use of the output
and so pauses other players. Native apps combine "play in background" with "mix
with others"; the web does not expose that combination. The **Keep playing in
the background** switch is that choice, made visible, with the trade-off spelled
out under it in the app. Android has no mixing control at all on the web, so
there the switch only governs the media session and what your music does is up
to Chrome. The status panel reports which case you are in.

## Architecture

Plain HTML, CSS and ES modules. No framework, no build step, no dependencies.

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
  wake-lock.js      screen wake lock (a convenience, not load bearing)
  pump.js           worker-backed scheduler heartbeat
  ui.js             small DOM builders
sw.js               offline shell
test/               node --test, no dependencies
```

The split is deliberate: everything with real logic in it (beat maths, tap
averaging, the ramp curve, settings validation) is a pure module with no DOM and
no audio, which is why it can be unit tested at all. Anything that touches a
browser API is a thin wrapper around it.

### Does this need a framework?

No. The app is one screen with roughly a dozen controls and no routing, no
server state, and no shared component tree. React or Svelte would add a build
step, a dependency tree to keep patched, and a deploy pipeline, in exchange for
solving problems this app does not have. The thing that was actually worth
fixing was the single 660-line `index.html`, and modules fixed that without
adding a toolchain. Staying build-free also means the source you read is the
source that ships, and GitHub Pages needs no CI.

Revisit that if the app grows a second screen, run history, or an account.

### Tests

`npm test` runs Node's built-in test runner. No dependencies, no config.

```
npm test
```

Coverage is aimed at the logic that is easy to get quietly wrong and hard to
notice on a run: beat times never drifting, a stall skipping missed beats rather
than firing them in a burst, the tap average settling and ageing out old taps,
the ramp curve hitting its target, and settings surviving corrupt or hostile
storage. The audio graph and the DOM are left to the browser, where an
integration test would cost far more than it caught.

Two bugs were found by writing these: `beatIndexAt` returned `-0` at the grid
origin, and `beatsElapsed` was off by one exactly on the first beat.

## Running it

Any static server:

```
python3 -m http.server 8777
```

Then open `http://localhost:8777`. A service worker caches the shell for offline
use; navigations go to the network first so a deploy is picked up on the next
launch with a connection.

## Deploying to GitHub Pages

1. Push, then enable Pages (Settings, then Pages, deploy from the branch root).
2. Open the page on your phone and add it to the home screen.

There is no cache version to bump by hand any more. The service worker revalidates
in the background and serves navigations network first.

## Testing it on a real run

The parts that matter most cannot be verified on a desktop, so check these on the
phone:

1. Start your music, then start Cadence, and confirm both are audible.
2. Switch to another app. The beat should continue.
3. Lock the screen. The beat should continue, and the lock screen should show
   Cadence with a stop control.
4. If your music pauses on iPhone, turn off **Keep playing in the background**
   and accept that the beat then stops when the screen locks. That trade is the
   platform's, not the app's.
