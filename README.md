# Cadence

A running metronome for the phone. It keeps time over your music, carries on
with the screen off, and installs to the home screen as a normal app.

No account, no tracking, no network calls. It works offline once installed.

## Features

- **Cadence from 100 to 220 steps per minute**, by slider, by one-step buttons,
  by named preset, or by tapping along to your own stride.
- **Eight sounds**, all synthesised at runtime: click, woodblock, beep, cowbell,
  rimshot, kick drum, hi-hat, and a drum kit that puts a kick on the accent and
  a hi-hat between.
- **Accent every 2, 3, 4 or 6 steps.** Two marks the same foot each time, which
  is how you feel whether your stride is even. Three and six line the beat up
  with breathing, in for three steps and out for three.
- **Cadence ramp.** Start where you actually run and climb to a target across
  the run, one step per minute at a time.
- **Vibration** instead of or alongside the sound, for treadmills and races.
- **Runs in the background**, so the beat survives an app switch and the screen
  locking.
- **Light and dark themes**, following your system setting.

## Installing

Open the app in your phone's browser and add it to the home screen. On iPhone
use Safari, since other browsers there cannot install a web app.

## Playing over your music

Start your music first, then start Cadence.

The **Keep playing in the background** switch controls a trade-off that differs
by platform. The app explains which case you are in, both under the switch and
in its status panel.

- **Android.** The beat continues with the screen off and through an app
  switch. Whether your music ducks or pauses is up to Chrome, and cannot be
  controlled from a web app.
- **iPhone.** Background playback and mixing with other audio are mutually
  exclusive, because of how Safari exposes audio sessions. Leave the switch on
  to keep the beat through a screen lock, at the cost of pausing other players.
  Turn it off to let your music keep playing, at the cost of the beat stopping
  once the app is hidden.

In both cases, fully closing the app stops the sound. Background audio needs a
running process, which a closed page does not have.

On Android, vibration is a reliable way to have the beat and your music at once.
iPhone does not expose vibration to the browser.

The platform behaviour behind all of this, and the sources for it, are in
[docs/background-audio.md](docs/background-audio.md).

## Browser support

| | Sound | Background | Mixing | Vibration | Lock screen controls |
| --- | --- | --- | --- | --- | --- |
| Android, Chrome | Yes | Yes | Browser decides | Yes | Yes |
| iPhone, Safari | Yes | Background or mixing, not both | See above | No | Yes |
| Desktop browsers | Yes | Yes | Yes | No | Varies |

## Running it locally

Any static server. There is no build step.

```
python3 -m http.server 8777
```

Then open `http://localhost:8777`.

## Deploying

The app is static files, so any host will do. For GitHub Pages, enable Pages in
the repository settings and deploy from the branch root. There is no cache
version to bump by hand: the service worker serves navigations network first,
so a deploy is picked up on the next launch with a connection.

## Development

```
npm test
```

Tests run on Node's built-in test runner, with no dependencies and no config.
Node 20 or newer.

- [docs/architecture.md](docs/architecture.md) covers the module layout, the
  scheduling model, and the structural decisions.
- [docs/background-audio.md](docs/background-audio.md) covers background
  playback, the platform constraints, and how to verify them on a device.
- [docs/cadence-training.md](docs/cadence-training.md) covers the running
  research behind the feature set.

Most of what makes this app interesting only happens on a real phone and cannot
be verified in a desktop browser. There is a device checklist in
[docs/background-audio.md](docs/background-audio.md#verifying-on-a-device).
