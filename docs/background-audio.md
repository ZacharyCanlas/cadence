# Background playback on Android

How Cadence keeps exact time over your music with the screen off, and the
Android rules it relies on.

## Audio focus: never requested

A runner's phone spends the run locked in a pocket with Spotify in front, and
the music has to keep playing at full volume. The web version paused Spotify,
because Chrome takes audio focus for a page and a page cannot opt out. Android
itself mixes players: "Two or more Android apps can play audio to the same
output stream simultaneously, and the system mixes everything together."

Cadence never calls `requestAudioFocus`. Android's fade and duck rules act only
on an app that "successfully requested audio focus", and the Android 12 fade-out
applies only "when an app requests audio focus while another app has the focus
and is playing". Spotify holds focus for the whole run, so nothing fades, ducks
or pauses it.

The track's `AudioAttributes` are `USAGE_MEDIA` with
`CONTENT_TYPE_SONIFICATION`. `USAGE_MEDIA` puts the beat on the music stream, so
the volume keys move the beat and Spotify together. The in-app slider scales the
beat alone through `AudioTrack.setVolume`, linear from 0 to 1. We passed on
`USAGE_ASSISTANCE_SONIFICATION`: it maps to the system stream, which the volume
keys skip by default, and the framework makes no guarantee about that mapping.

The app holds no `MediaSession`. Media buttons go to "the last app with a
MediaSession that played audio locally", so your earbuds' play and pause stay
with Spotify.

## The foreground service

Start launches `RunService`, a started, unbound foreground service of type
`mediaPlayback`, for the length of the run. Android sets no timeout on
`mediaPlayback`. `onStartCommand` returns `START_NOT_STICKY`, so a killed
process never restarts the service without a run. Swiping the app away from
recents keeps the run going, as music players do. The service holds no wake
lock: "If you're playing audio, the audio system sets and manages a wake lock
for you."

The ongoing notification carries the cadence as its title, the ramp or call
status as its text, the run clock as a chronometer, and Slower, Faster and Stop,
marked as allowed without unlocking (`setAuthenticationRequired(false)`; the OS
has the final say). Tapping it opens the app. Android 14 lets you dismiss it
while unlocked, and the service then posts nothing more that run, so a ramp step
or a call cannot bring it back; the next run posts it again. With notifications
denied, the run starts anyway and the Output section says Stop is only in the
app.

## The frame clock

The beat is a PCM stream, and the frames written are the clock.

- `TrackOutput` opens an `AudioTrack` in `MODE_STREAM`: mono float PCM at the
  device's native output rate (48 kHz when the phone reports none),
  `PERFORMANCE_MODE_NONE`, and a buffer of about 100 ms, or the device minimum
  when that is larger.
- One thread, `cadence-render`, runs at `THREAD_PRIORITY_URGENT_AUDIO`. It
  renders 20 ms of audio, writes it with a blocking write, and repeats. The
  blocking write paces the loop to the hardware, so the loop has no timer.
- A beat is a frame index in that stream. A late thread can starve the track,
  which is an underrun, and still cannot move a beat: the next frame it writes
  is the next frame of the grid.
- The controller hands settings over in a snapshot that the render thread reads
  once per 20 ms buffer. A tempo, sound or accent change lands on the next beat
  not yet written.

You hear each frame later than it is written, by the track's buffer plus the
route's latency, and Bluetooth adds a lot. `PlaybackClock` maps now to the frame
being heard from the track's `AudioTimestamp`, re-queried about every 10 s. Until
a timestamp arrives (at Start, after a route change, or on a route that never
reports one) it falls back to the playback head. The beat flash, the step count
and every vibration read this clock.

Output failures never leave a silent "running". A track that fails to open at
Start leaves the run stopped, with a snackbar. A write returning
`ERROR_DEAD_OBJECT` recreates the track once, and the run carries on from the
same write head, losing at most the buffer that failed. A second dead object
within a second, any other write error, or `startForeground` throwing stops the
run with "The beat stopped because the audio output failed." Each track logs its
underrun count when it closes, as `Track closed after N underruns` under the
logcat tag `Cadence`.

## Calls and disconnects

`RunService` registers an `AudioManager` mode listener, and the controller reads
the mode at Start. Any mode but `MODE_NORMAL` silences the beat: ringing, a
carrier call, a VoIP call, call screening, and any mode Android adds later. It
needs no permission. We passed on `TelephonyCallback`, which needs
`READ_PHONE_STATE` and sees carrier calls only.

While silenced, the render thread keeps writing silence, so the grid keeps its
phase. Beats in that window advance the accent count, so the first beat after
the call carries the right accent, but they play nothing, buzz nothing and count
as no steps. The run clock counts only frames written while not silenced, and
the ramp reads that clock, so both pause for the call and pick up where they
left off. The notification shows "Paused for a call" and drops its chronometer,
then posts it again from the app's clock.

`RunService` also listens for `ACTION_AUDIO_BECOMING_NOISY`, "sent when a wired
headset is unplugged, or when an A2DP audio sink is disconnected", and stops the
run the way Stop does. We ruled out carrying on through the phone speaker.

## Vibration

`VibratorHaptics` plays a 45 ms one-shot per beat, 90 ms on an accent, with
`VibrationAttributes` usage `USAGE_ALARM`. AOSP's `VibrationSettings` keeps two
allowlists that matter on a run: usages allowed from a background process, and
usages kept under Battery Saver. `USAGE_ALARM` is on both. `USAGE_MEDIA` is
missing from the Battery Saver list, so a vibrate-only run would go still the
moment you turned Battery Saver on. Alarm haptics follow the phone's alarm
vibration setting, as the Vibrate hint says. Each vibration is armed when its
beat is written and posted on a `HandlerThread`, delayed until the playback
clock reaches the beat's frame.

## Verifying on a device

The JVM tests drive the renderer and the controller against fakes. Audio
routing, calls, vibration and a backgrounded app show up only on a phone, so
install the release APK and run each check below. For the underrun count,
install Android platform-tools on the computer, connect the phone with USB
debugging on, and run `adb logcat -s Cadence`.

1. **Mixing.** Play Spotify, then Start Cadence. Pass: both are audible at full
   volume, Spotify's volume holds at Start and through a 10-minute run, and Stop
   leaves Spotify playing. Then start Cadence first and Spotify second. Pass:
   both play.
2. **Bluetooth.** Repeat step 1 on Bluetooth earbuds. Pass: both streams come
   through the earbuds.
3. **An hour, locked.** Run 60 minutes at 180 with the screen locked and Spotify
   in front. Pass: the run clock reads 60:00 when the phone's clock says an hour
   has passed (±1 s), and the step count reads 10,800 (±1). A larger gap means
   underruns, which logcat's count confirms.
4. **Timing by ear.** Across the same run, change the tempo, sound and accent in
   the app, and the tempo with the notification's Slower and Faster. Pass: no
   drift or stutter, and each change lands on the next beat with no stumble.
5. **Controls.** Use every control: cadence slider and ±1, tap your stride, the
   six presets, the eight sounds and volume, the accents, Click and Vibrate, the
   ramp, the clock and steps. Pass: each works and persists, with the web
   version's ranges, defaults, presets, accents and hints. A/B the eight sounds
   against the web version while its site is still up. Pass: they sound alike.
6. **Earbud buttons.** Mid-run, press play/pause on the earbuds. Pass: Spotify
   pauses and resumes, and the beat carries on.
7. **Notification.** Lock the phone mid-run. Pass: the lock screen shows the
   notification with its title, text and a running chronometer; Slower, Faster
   and Stop work without unlocking; tapping it opens the app. Then unlock and
   dismiss it mid-run. Pass: it stays gone through a ramp step and a call, the
   beat carries on, Stop in the app ends the run, and the next run shows it.
8. **Disconnect.** Mid-run on earbuds, turn them off. Pass: the beat stops and
   the app shows "Stopped when your headphones disconnected".
9. **Vibration.** Turn Click off and Vibrate on, then run with the screen off
   for 10 minutes. Pass: every beat buzzes, accents longer. Repeat with Battery
   Saver on.
10. **Calls.** Mid-run with the ramp climbing, take an incoming carrier call,
    then a WhatsApp call. Pass: silence from ringing to hang-up, then the beat
    resumes on the grid. The step count leaves out the silent stretch, the run
    clock reads the same after the call as before it, and the ramp resumes from
    the cadence it had reached. The notification shows "Paused for a call"
    during the call, and its chronometer matches the app's clock afterwards.
11. **Settings.** Change several, force stop the app, reopen. Pass: all kept.
12. **Preview.** Stopped, Click on, pick a sound, then an accent. Pass: each
    pick plays one beat.
13. **Screen.** Pass: the screen stays on while the app is in front and running,
    and sleeps on its usual timeout otherwise.
14. **Ramp hold.** Choose 150, ramp to 180, Start, and press Slower in the
    notification partway up. Pass: the cadence holds there until Stop, and the
    notification reads "Holding at N. You took over from the ramp." Stop shows
    150, and the next run climbs from 150 again.
15. **Swipe away.** Mid-run, swipe the app away from recents. Pass: the beat and
    the notification continue, and Stop in the notification ends the run.
16. **Route changes.** Mid-run on the speaker, connect Bluetooth earbuds. Then
    move the output between two connected devices in the system output switcher.
    Pass, each time: the beat, vibration and step count carry on along the grid,
    or the run stops with the audio output failure message, and it never shows
    "running" with no sound. A move from earbuds to the speaker can send the
    disconnect broadcast, so the headphones message passes there too.

## Sources

- [Manage audio focus, Android Developers](https://developer.android.com/media/optimize/audio-focus)
- [Keep the device awake, Android Developers](https://developer.android.com/develop/background-work/background-tasks/awake)
- [Responding to media buttons, Android Developers](https://developer.android.com/media/legacy/media-buttons)
- [Foreground service timeouts, Android Developers](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [AudioTrack reference](https://developer.android.com/reference/android/media/AudioTrack)
- [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager)
