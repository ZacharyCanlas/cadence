# Cadence training, and why the features are what they are

Research notes behind the feature set. Every control in the app should trace
back to something runners actually do, rather than to something a metronome
could technically offer.

## Why cadence at all

Cadence is steps per minute, counting both feet. Coaching interest in it comes
from its relationship to overstriding: at a given pace, a higher cadence means
shorter steps, which tends to move the foot strike closer to under the body
rather than out in front of it. The commonly quoted target is around 180 steps
per minute, a number that traces back to observations of elite runners and has
been repeated widely enough to become the default coaching reference point.

Two qualifications matter for the design, and both come up repeatedly in
coaching material:

- **180 is a reference, not a prescription.** Appropriate cadence varies with
  height, leg length, pace and terrain. Most recreational runners sit somewhere
  in the 160s.
- **Change should be incremental.** The standard advice is to add roughly five
  percent to your current cadence and let it settle, rather than jumping
  straight to a target number. Turnover is a trained habit, and forcing a large
  step change tends to produce a stiff, deliberately choppy stride.

That second point is the single most consequential finding for this app, and it
is what the ramp feature exists to serve.

## What the features come from

### Range of 100 to 220 steps per minute

Running metronome apps generally cover something like 100 to 220. The useful
running band is narrower, roughly 150 to 200, but the wider range covers a
walking warm-up at the bottom and short fast strides at the top.

### Named presets rather than bare numbers

Saved cadence targets with names like "Recovery run 165" or "Tempo 180" are a
common feature, and the naming is the point: a label tells you when to use a
setting, a number does not. The presets in the app are:

| Cadence | Name | Use |
| --- | --- | --- |
| 165 | Recovery | Easy legs, low impact |
| 170 | Easy | Conversational pace |
| 175 | Steady | Most weekly mileage |
| 180 | Target | The usual coaching reference |
| 185 | Tempo | Threshold and race effort |
| 190 | Strides | Short, fast turnover |

### Tap tempo

Reading your existing cadence is the first step of any cadence plan, since the
advice is to work from where you are. Tapping along to your own stride is the
standard way apps do this without a sensor. The implementation keeps a rolling
average of the last few intervals and waits for three taps before showing a
reading, so a single mistimed tap does not throw the number.

### Accent every 2, 3, 4 or 6 steps

An accent beat, where one beat in a bar sounds different, is a standard
metronome feature, and for running each division means something specific:

- **Every 2.** The accent lands on the same foot each time. This is how you
  feel whether your left and right strides are even, which is useful for
  anyone working around an old injury.
- **Every 3 and every 6.** These line the beat up with breathing. A commonly
  taught pattern is inhaling for three steps and exhaling for three, which
  gives an odd-numbered cycle so the exhale, and the higher impact loading that
  comes with it, does not always fall on the same foot. Every 3 marks each
  phase; every 6 marks the full cycle.
- **Every 4.** A conventional four count for anyone who simply finds a bar
  easier to run to.

### A library of sounds

The larger metronome apps ship twenty or more sounds, and the reason is
environmental rather than aesthetic: a sound that cuts through traffic is
different from one that sits comfortably under music through headphones on a
quiet trail. Cadence synthesises eight on the phone rather than shipping audio
files, so each accent is a re-pitched variant of its own voice.

Each is described in the app by where it works, not by what it is:

| Sound | Suited to |
| --- | --- |
| Click | Traffic and wind |
| Woodblock | General use, the classic metronome sound |
| Beep | Headphones |
| Cowbell | Noisy roads |
| Rimshot | Sitting behind music |
| Kick drum | Felt more than heard, good with music |
| Hi-hat | Staying out of the way of vocals |
| Drum kit | Kick on the accent, hi-hat between |

### Cadence ramp

This follows directly from the incremental-change finding. Rather than setting
a target and enduring it, the ramp starts at the cadence you are comfortable
at and walks it to the target across a chosen number of minutes, one step per
minute at a time, slowly enough that no single change is noticeable.

The curve is computed from elapsed time rather than accumulated, so a throttled
timer in the background cannot make the ramp finish somewhere other than the
target.

### Vibration

Haptic feedback is offered by most running metronomes for treadmills, races
where audio is impractical, and group runs. Cadence uses the phone's alarm
vibration, which keeps buzzing under Battery Saver.

## Deliberately not built

- **Cadence graphs and run history.** These need a motion sensor or a recorded
  session to be meaningful. The app has no sensor access and stores nothing
  beyond your settings.
- **Music library integration.** Matching a track's BPM to a target cadence is
  a real feature in some apps, but it needs a music service account and network
  calls, and the app has neither.
- **Interval and workout timers.** Plausible, but a different product. The ramp
  covers the cadence-specific case.

## Sources

- [Running metronome guide, Runo](https://www.runoapp.com/blog/running-metronome-guide)
- [Improve running cadence with a metronome, Runo](https://www.runoapp.com/blog/how-to-improve-running-cadence-with-metronome)
- [Best running metronome apps, Runo](https://www.runoapp.com/blog/best-running-metronome-apps)
- [Run efficiency part 1: cadence, Process 3](https://process3.com.au/run-efficiency-part-1-cadence/)
- [The quickest way to improve your running with a metronome, ChiLiving](https://chiliving.com/the-quickest-way-to-improve-your-running-with-a-metronome/)
- [Cadence and breathing, Ask Run Guy](https://askrunguy.com/cadence-and-breathing/)
- [How to use a metronome or cadence tool, Philotimo Running Coach](https://www.philotimorunningcoach.ca/blog/metronome-cadence-running-efficiency)
- [Metronome for running, 180 SPM cadence, True Metronome](https://truemetronome.app/blog/metronome-for-running/)
- [RunTempo, runners metronome](https://apps.apple.com/us/app/runtempo-runners-metronome/id646414918)
