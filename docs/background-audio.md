# Background playback on the web

Why the metronome keeps time with the screen off, what it cannot do, and the
platform behaviour behind both.

## The problem

A metronome is only useful if it keeps time for the whole run, and a runner's
phone spends that run locked in a pocket with a music app in the foreground.
Browsers work against this deliberately. A page that is not visible gets its
timers throttled, and a page that is not audible gets suspended outright. Both
are battery optimisations, and both stop a naive metronome dead within seconds
of the screen going off.

Nothing here is a workaround for a bug. These are the documented rules, and the
approach below is about staying on the right side of them.

## What browsers actually do

Three separate mechanisms matter, and they fire at different moments:

1. **Timer throttling.** Once a page is hidden, `setTimeout` and `setInterval`
   are clamped, commonly to once per second and sometimes to once per minute
   after a page has been hidden for a while. A worker's timers are throttled
   less aggressively than the main thread's, but they are not exempt.
2. **Page suspension.** A hidden page that is not doing anything the browser
   considers important becomes a freeze candidate. Once frozen, no JavaScript
   runs at all.
3. **Audio focus and session category.** The operating system decides which app
   owns the output. This is what determines whether other players keep going,
   duck, or pause, and on iOS it also determines whether audio is allowed to
   continue in the background at all.

Playing audio is the strongest signal a page can send against the first two.
A page the browser considers to be actively playing media is not a freeze
candidate and does not get the harshest timer clamps.

## The approach

Four mechanisms, layered so no single one is load bearing.

### 1. Schedule ahead on the audio clock

Beats are never fired by a timer. Each one is placed on the audio hardware
clock in advance, at `anchor + index * (60 / bpm)`, and the hardware plays it
whether or not JavaScript is running at that moment. A timer that arrives late
therefore costs nothing, as long as it arrives before the queue drains.

### 2. Extend the queue the moment the page hides

This is the part that does the real work.

| State | Lookahead | Reason |
| --- | --- | --- |
| Visible | 0.25s | Tempo changes need to feel instant |
| Hidden | 6s | A pump throttled to 1Hz still finds seconds of audio committed |

The switch happens on `visibilitychange`, before throttling has a chance to
bite. Six seconds is chosen to survive the common one-second clamp with a wide
margin, while staying short enough that the queue is cheap to revoke.

### 3. Hold a media session

A near-silent WAV loop, generated at runtime rather than shipped as an asset,
plays through an `<audio>` element for as long as the metronome runs. This is
what registers the page as active media playback, which keeps it resident and
puts the app on the lock screen through the Media Session API, with working
play, pause and stop controls.

The loop is deliberately **not** `muted` and not at zero volume. A silent
element is treated as inaudible by some browsers and gets suspended along with
the rest of the page. It carries a couple of least significant bits of dither
instead: inaudible in practice, but not digital silence.

### 4. Wake lock, as a convenience only

The screen wake lock is requested while running, but nothing depends on it. It
exists so the display does not sleep while you are looking at the app. The
system drops it whenever the page is hidden, so it is re-taken on return.

## Everything queued is revocable

Scheduling six seconds ahead means six seconds of decisions are already
committed to the hardware. If a tempo change, a new sound or a stop simply
waited for that to drain, the app would feel broken.

Each queued beat therefore keeps a handle on the nodes and timers it created,
routed through its own gain node so it can be silenced without unpicking
whatever graph the voice built underneath. Changing anything revokes the
affected beats and re-plans from a point far enough ahead to be reliable
(50ms), then re-queues.

Revocation also hands the beat numbering back to where it was, so the accent
pattern does not shift when the tempo does. A tempo change keeps the beat that
just sounded exactly where it was and applies the new spacing from there, so
there is no audible phase jump mid-run.

## The platform matrix

### Android, Chrome

Background playback works. The media session keeps the page resident, and the
extended lookahead covers the throttling.

Mixing with other audio is not controllable from the web. Chrome decides
whether your music ducks or pauses when the page takes audio focus, and there
is no API to influence it. Starting the music first and then starting Cadence
is the only way to find out what a given device does.

### iOS, Safari

Background playback and mixing with other audio are **mutually exclusive**.
This is the significant constraint in the whole app, and it is not something
better engineering can route around.

Safari's [Audio Session API](https://github.com/w3c/audio-session/blob/main/explainer.md)
exposes a session type, and the two relevant values behave like this:

| Type | Mixes with other audio | Survives backgrounding | Silenced by the ringer switch |
| --- | --- | --- | --- |
| `ambient` | Yes | No | Yes |
| `playback` | No | Yes | No |

Native apps get around this by combining the `playback` category with the
`mixWithOthers` option, which is exactly the pairing a running metronome wants.
The web does not expose that option. Every native running metronome that keeps
playing over Spotify with the screen locked is using an API the browser does
not offer.

So the app surfaces the choice instead of pretending it does not exist. The
**Keep playing in the background** switch selects the session type, the
consequence is written under the switch, and the status panel reports which
case the current device is in.

### Both platforms

Fully closing the app stops the sound. Background audio needs a process to stay
alive, and a closed page has none. No web app can do this; it requires a native
background service.

## Verifying on a device

None of this can be confirmed in a desktop browser, because the behaviour under
test is what the mobile OS does to a backgrounded page. On a phone:

1. Start music, then start Cadence. Confirm both are audible.
2. Switch to another app. The beat should continue.
3. Lock the screen. The beat should continue, and the lock screen should show
   Cadence with a stop control.
4. Leave it locked for several minutes. The beat should not drift or stutter.

If the music pauses on iPhone, that is the session type trade above. Turning
off background mode restores mixing and gives up screen-off playback.

## Sources

- [Audio Session API explainer, W3C](https://github.com/w3c/audio-session/blob/main/explainer.md)
- [AudioSession.type, MDN](https://developer.mozilla.org/docs/Web/API/AudioSession/type)
- [iOS web apps and the Media Session API, David Bushell](https://dbushell.com/2023/03/20/ios-pwa-media-session-api/)
- [iOS audio lock screen problem in a PWA, Apple Developer Forums](https://developer.apple.com/forums/thread/762582)
- [Media playback in a PWA with the phone locked, Discourse Meta](https://meta.discourse.org/t/media-playback-with-pwa-keep-playing-when-phone-locked/182219)
- [Web Audio in service workers for Media Session, w3c/mediasession issue 232](https://github.com/w3c/mediasession/issues/232)
- [Audio capabilities of PWAs today](https://whatpwacando.today/audio/)
- [Audio player PWA behaviour, Progressier](https://progressier.com/pwa-capabilities/audio-player-pwa)
