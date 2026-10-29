# Cadence

A running metronome for Android. It keeps time over your music for a whole
run, with the screen off and another app in front, and your music keeps
playing at full volume.

No account, no tracking, no network calls.

## Features

- **Cadence from 100 to 220 steps per minute**, by slider, by one-step buttons,
  by named preset, or by tapping along to your own stride.
- **Eight sounds**, all synthesised on the phone: click, woodblock, beep,
  cowbell, rimshot, kick drum, hi-hat, and a drum kit that puts a kick on the
  accent and a hi-hat between.
- **Accent every 2, 3, 4 or 6 steps.** Two marks the same foot each time, which
  is how you feel whether your stride is even. Three and six line the beat up
  with breathing, in for three steps and out for three.
- **Cadence ramp.** Start where you run now and climb to a target across the
  run, one step per minute at a time. Change the cadence mid-ramp and it holds
  where you put it for the rest of the run; the cadence you chose stays saved.
- **Vibration** instead of or alongside the sound, for treadmills and races. It
  uses your phone's alarm vibration setting and keeps buzzing under Battery
  Saver.
- **Plays over your music.** Cadence never requests audio focus, so Spotify or
  any other player keeps its volume and keeps your earbuds' play and pause
  buttons.
- **A notification while you run**, with the cadence, the run clock, and
  Slower, Faster and Stop from the lock screen. Swiping the app away keeps the
  run going. A call silences the beat and pauses the clock and the ramp;
  disconnecting your earbuds stops the run.
- **Light and dark themes**, following your system setting.

## Building

Gradle runs on JDK 21, which the Paparazzi screenshot plugin needs; the app's
bytecode targets Java 17. The app compiles against API 37 (the pinned
Compose 1.12 requires it), targets API 36, and runs on Android 13 (API 33) and
later. Install the SDK with the Android CLI that ships in cmdline-tools, and
put its path in `local.properties` as `sdk.dir`:

```
android sdk install platforms/android-37.0 build-tools/36.0.0 platform-tools
./gradlew assembleRelease
```

This writes `app/build/outputs/apk/release/app-release.apk`, minified with R8
and signed with the release key. Signing reads four Gradle properties from
`~/.gradle/gradle.properties`: `CADENCE_KEYSTORE` (the keystore's path),
`CADENCE_KEYSTORE_PASSWORD`, `CADENCE_KEY_ALIAS` and `CADENCE_KEY_PASSWORD`.
If any is missing, the build fails and names it; it never falls back to the
debug key. Debug builds, tests and lint need none of them.

## Installing

Copy `app-release.apk` to the phone by Google Drive or USB cable, then tap it
in Files or Drive. The first time, Android asks you to allow "Install unknown
apps" for the app that opened it. Allow it once.

To check that a copy came from us, run
`apksigner verify --print-certs app-release.apk` (from the SDK's build-tools)
before installing. The certificate's SHA-256 digest must be
`b4dbb24e75bc35f1b615d4b95148316e13583edc2f2e925ff3d7bdd4beffa913`, which
other tools print as
`b4:db:b2:4e:75:bc:35:f1:b6:15:d4:b9:51:48:31:6e:13:58:3e:dc:2f:2e:92:5f:f3:d7:bd:d4:be:ff:a9:13`.

## Updating

Install the new APK over the old one the same way; your settings carry over.
Android refuses an APK whose `versionCode` is the same as or lower than the
installed copy's, so every APK we hand out bumps `versionCode` and
`versionName` in `app/build.gradle.kts`, and we commit the bump before the APK
leaves the machine. An update also needs the same signing key: a copy signed
with any other key installs only after you uninstall the old one and lose its
settings.

## Development

```
./gradlew test lint assembleDebug
```

CI runs the same command on every push and pull request. Tests are JUnit 4 on
the JVM. `./gradlew recordPaparazziDebug` renders the screen in light, dark
and font scales 1.3 and 2.0 into `app/src/test/snapshots/`, with no emulator.

- [docs/architecture.md](docs/architecture.md): the module layout, the
  scheduling model, and the structural decisions.
- [docs/background-audio.md](docs/background-audio.md): background playback,
  the platform constraints, and how to verify them on a device.
- [docs/cadence-training.md](docs/cadence-training.md): the running research.
- [docs/findings.md](docs/findings.md): what review found that tests cannot settle.

Background playback, calls, earbuds and vibration only show up on a real phone.
There is a device checklist in
[docs/background-audio.md](docs/background-audio.md#verifying-on-a-device).

Barlow Condensed is bundled under the SIL Open Font License
([third_party/barlow-condensed/OFL.txt](third_party/barlow-condensed/OFL.txt)).
