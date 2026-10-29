# Architecture

How the app is put together, and the reasoning behind the structural choices.

## Layout

One Gradle module, `:app`, at the repository root. Version pins live in
`gradle/libs.versions.toml`; SDK levels, R8 and the release signing check live
in `app/build.gradle.kts`.

```
app/src/main/kotlin/io/github/zacharycanlas/cadence/
  CadenceApp.kt              Application; owns the one MetronomeController
  MainActivity.kt            the one Activity: frame-clocked flash, permission prompt, snackbars
  core/                      pure Kotlin; imports nothing from android.*
    BeatGrid.kt              cadence range and beat maths (anchor + n * period)
    Renderer.kt              writes the beat into the PCM stream; the frame clock
    Voices.kt                the eight synthesised voices, biquads and noise
    BeatFeed.kt              written beats waiting to be heard, and the honest step count
    Cadence.kt               the cadence chosen vs the cadence sounding, and the ramp hold
    Ramp.kt                  cadence-ramp maths
    TapTempo.kt              rolling-average tap detection
    Settings.kt              validated settings over a typed key-value store
    Presets.kt               preset cadences and accent values
    Format.kt                clock and number formatting
  run/
    MetronomeController.kt   the process-wide metronome: run state, settings, StateFlows
    PlaybackClock.kt         maps now to the stream frame being heard
    Ports.kt                 AudioOutput, Haptics, RunHost: how the controller reaches Android
    RunService.kt            foreground service: notification, disconnect receiver, call watcher
  platform/                  thin wrappers around Android APIs
    TrackOutput.kt           the AudioTrack, which never requests audio focus
    VibratorHaptics.kt       USAGE_ALARM one-shots posted on a HandlerThread
    PrefsStorage.kt          SharedPreferences behind SettingsStorage
  ui/
    CadenceScreen.kt         the one screen: band, modules, docked Start/Stop
    Theme.kt                 colour roles, band colours, Barlow Condensed figures
app/src/main/res/            every user-facing string, icons, the bundled font
app/src/test/kotlin/         JUnit 4: core, the controller against fakes, screenshots
third_party/barlow-condensed/ the font's licence
```

## The organising principle

Modules fall into three groups, and the split is what makes the app testable.

**Pure logic, no Android.** Everything in `core/`. This is the code that is
easy to get wrong without anyone noticing on a run: an off-by-one in beat
numbering or a ramp that overshoots its target throws no error, it sounds
wrong for forty minutes. All of it runs under plain JUnit. The renderer
belongs here too: it fills a buffer and reports the beats it started, so tests
drive it frame by frame with no audio device.

**Thin wrappers around Android APIs.** Everything in `platform/`, and
`RunService`. Each is small enough to read in one sitting and does nothing
clever, because the OS does the complicated part. We verify these on a phone
rather than mock them.

**The controller and the screen.** `MetronomeController` is the composition
root. It owns the run, the cadence, the feed and the settings, publishes them
as `StateFlow`s, and holds the rules the controls rely on: settings are saved
as `normalize()` leaves them, ramp output is never saved, and a manual change
mid-ramp holds instead of choosing. It reaches Android only through four
interfaces (`AudioOutput`, `Haptics`, `SettingsStorage`, `RunHost`), so its
tests run on the JVM against fakes. `CadenceScreen` draws `RunState` and
`Settings` and calls plain lambdas in `ScreenActions`, so screenshots need no
controller.

## Why native

The PWA's beat paused Spotify on Android, because Chrome takes audio focus for
the page and a web page cannot opt out. Android mixes apps that play at the
same time, and its fade and duck rules act only on apps that requested
focus. A native app that never requests it plays alongside Spotify with
nothing paused or ducked.

Flutter and React Native were the alternatives. Each would still need native
Kotlin for every part that fixes the problem (the no-focus `AudioTrack`, the
foreground service, the disconnect receiver, the call watcher), and their
common audio plugins request focus by default. With no second platform to
share code with, the bridge buys nothing, so the app is Kotlin and Jetpack
Compose.

`core/` imports nothing from `android.*`, the rule that kept the PWA's
`src/core/` free of Vue. We hold it as a package convention rather than pay
for a separate JVM module's second build script.

## The scheduling model

Covered in depth in [background-audio.md](background-audio.md). In short:

- The frames written are the clock. One render thread at
  `THREAD_PRIORITY_URGENT_AUDIO` renders 20 ms buffers and writes them, with
  blocking writes, to a `MODE_STREAM` float `AudioTrack` at the device's native
  rate, holding about 100 ms. A beat is a frame index in that stream, so thread
  jitter can cause an underrun and never a misplaced beat.
- Beat starts derive from an anchor (`anchor + index * 60/bpm`) and round to a
  frame only when mixed, so float error cannot pile up. An hour at 48 kHz is
  1.7e8 frames, exact in a Double.
- Settings apply at buffer boundaries. The controller replaces a `@Volatile`
  `RenderSettings` snapshot whole, and the render thread reads it once per
  buffer, so a new voice, accent or click lands on the next beat not yet
  written.
- On a tempo change, the first beat at the new spacing goes at
  `max(last + new period, writeHead)`, where `last` is the latest beat already
  mixed. The gap is exactly the new period on a drop and between the new and
  old periods on a rise, so no beat lands on one already sounded and a rise
  never skips a slot.
- A stalled writer underruns, and the next frame it writes is still the next
  grid frame, so nothing skips ahead.
- Written beats cross to the main thread through a `ConcurrentLinkedQueue`.
  `PlaybackClock` maps now to the frame being heard, from the track's latest
  `AudioTimestamp`, and falls back to the playback head while there is none.
  The beat flash on each Compose frame, the step count on a 500 ms tick (so it
  stays right with the screen off) and each vibration's delay all read that
  clock, so they include Bluetooth output latency.

Four invariants are worth stating because they are easy to break:

1. **Accent phase is tracked separately from grid index.** A tempo change
   re-anchors the grid and resets its index to 0; the accent follows the
   running count, which only ever advances, so the pattern carries straight
   through. Beats inside a call advance the count as well, so the first beat
   after the call carries the accent the grid predicts.
2. **A written beat is final.** The renderer reports each beat once, as it
   writes it, and never withdraws one: settings change the next buffer, never
   a buffer already written. The feed counts and flashes beats on that basis,
   so a change that unwound written audio would count beats that never
   sounded.
3. **Anything derived from time is computed from elapsed, not accumulated.**
   The run clock is the frames written while not silenced, divided by the
   rate, and the ramp reads that clock. A late tick changes nothing about
   where either ends up, and a call pauses both. Underruns make the run clock
   fall behind the phone's clock, which is how a device check spots them.
4. **The cadence you chose and the cadence sounding are different numbers.**
   Only the first is ever saved. Persisting the second lets a ramp overwrite
   its own starting point, so it works exactly once. A manual change during a
   running ramp moves only the sounding cadence, for the rest of that run.
   `core/Cadence.kt` holds the split.

## Sound synthesis

The eight voices in `core/Voices.kt` are synthesised per hit, when a beat is
first mixed, from oscillators, biquads and a one-second noise buffer seeded
per run. The app ships no audio files. The accent is the same voice pitched up
by 1.5 and played 1.35 times louder, and each noise hit starts at a random
offset into the buffer, so repeated hits never loop identically.

The voices match the PWA's Web Audio sound. Biquads use the Web Audio spec's
coefficients, with highpass Q in decibels and bandpass Q linear. Squares and
triangles are odd-partial Fourier series, cut off the way Chrome's band-limited
tables are, which keeps every partial below Nyquist. Against renders from the
PWA, every voice matched within 1.2% on peak, on a 5 ms RMS envelope and on
spectral centroid. Each hit renders from index 0 and the renderer places it,
so a hit that starts near the end of a buffer carries its tail into the next.

## The run service

`CadenceApp` owns one `MetronomeController` for the process, so a run outlives
the Activity. Start launches `RunService`, a started, unbound foreground
service of type `mediaPlayback`, which Android never times out. The service:

- posts the ongoing notification: cadence as the title, the run clock as a
  chronometer, and Slower, Faster and Stop, which call the same controller as
  the screen;
- registers the `ACTION_AUDIO_BECOMING_NOISY` receiver, which stops the run
  when earbuds disconnect;
- listens for audio mode changes. Any mode but `MODE_NORMAL` silences the
  beat, so a mode Android adds later fails closed.

`onStartCommand` returns `START_NOT_STICKY`, so a killed process never
restarts the service without a run. Swiping the app away from recents keeps
the run going, as music players do. The app holds no `MediaSession`, because
media buttons go to the last app with a session that played audio, and that
keeps the earbuds' buttons on the music.

Threads: the render thread alone writes the renderer's state, haptics post to
their own `HandlerThread`, and everything else belongs to the main thread.

The app never shows "running" with no sound. A track that fails to open at
Start leaves the run stopped, with a snackbar. A write returning
`ERROR_DEAD_OBJECT` recreates the track once and carries on from the same
write head. A second dead object within a second, any other write error, or
`startForeground` throwing stops the run with the same kind of snackbar.

## Testing

`./gradlew test` runs JUnit 4 on the JVM. There is no Robolectric and no
instrumented test. `./gradlew lint` runs AGP's lint, which fails the build on
errors. CI runs both, plus `assembleDebug`, on every push and pull request.

The `core/` tests pin beat spacing within a frame over an hour, the first beat
80 ms after Start, a hit spanning a buffer boundary rendering the same samples
as in one buffer, the ramp curve, tap tempo, and settings surviving
out-of-range, hostile and wrong-typed storage. Two sweeps pin the tempo
change: it never lands a beat on top of one sounded (no gap under 60/220 s)
and never swallows one (no gap over the old period), each at buffer sizes of
1, 64 and 4800 frames. 120 to 132 at 1.04 s with a one-frame buffer is the
case where re-anchoring at the write head alone drops a beat.

The controller runs against `run/Fakes.kt`. `FakeOutput` simulates a track
whose writer runs a fixed latency ahead of what is heard, so a test knows when
each frame sounds. The controller tests cover start and stop order, ramp
output never saved, hold against choose, calls (an unknown mode included),
earbuds disconnecting, stop reasons, the playback clock with and without
timestamps and across a recreated track, dead objects, and 10,000 beats
crossing threads with none lost or doubled.

A test suite that runs green says nothing on its own about whether it would go
red. Each sweep and guard test has been checked against a deliberately broken
copy of the code it covers. Repeat that when adding to these files: write the
mutation, confirm the red, then revert.

None of this says what a real phone does with a backgrounded app, audio
routing, calls or vibration. We verify those by hand on a device using the
checklist in
[background-audio.md](background-audio.md#verifying-on-a-device).

`./gradlew recordPaparazziDebug` renders the screen with Paparazzi (layoutlib
on the JVM) in light, dark and font scales 1.3 and 2.0, viewport and scroll.

## Design system

Tokens live in `ui/Theme.kt`: Material 3 colour roles, the band's own colours
in `BandColors`, shapes, and a type scale that sets figures in Barlow
Condensed. The screen is a pace band. A cobalt band at the top carries the
cadence in tabular figures, read at arm's length in daylight while moving.
Below it, ruled modules under ink tabs hold the setup, read standing still.
Presets, sounds and accents are a printed cell grid, the ramp is a set of
split rows, and while the ramp climbs the band shows a split strip. Start and
Stop sit docked at the bottom.

Conventions worth preserving:

- Tabular figures are for measurements only (cadence, clock, counts, splits).
- Hairline rules separate modules; nothing is boxed as a card.
- Lime marks "you are here", the ramp split the run has reached and the beat
  flash, and nothing else.
- Light and dark are both first class; this is used at 6am and at 9pm.
- The beat flash is a colour held for 120 ms with no movement, so it reads the
  same with animations off.
- Every user-facing string lives in `res/values/strings.xml`.
- Text must fit up to font scale 2.0; large text stacks a slider's label and
  value over the slider.
