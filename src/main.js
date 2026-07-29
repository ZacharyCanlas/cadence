/**
 * Composition root: builds the controls, wires them to the engine, and keeps
 * the two in step. Anything with real logic in it lives in its own module.
 */

import { createMetronome } from './audio-engine.js';
import { MAX_BPM, MIN_BPM } from './beat-grid.js';
import { createCadence } from './cadence.js';
import { createKeepAlive } from './keep-alive.js';
import { createMediaSession } from './media-session.js';
import { capabilities, isIOS } from './platform.js';
import { ACCENTS, PRESETS } from './presets.js';
import { rampDone, rampProgress } from './ramp.js';
import { load, save, safeLocalStorage } from './settings.js';
import { VOICES, findVoice } from './sounds.js';
import { createTapTempo } from './tap-tempo.js';
import { $, check, formatClock, formatSteps, segmented, toggle } from './ui.js';
import { createWakeLock } from './wake-lock.js';

const storage = safeLocalStorage();
let settings = load(storage);

/** Beats scheduled but not yet reached, drained by the animation loop. */
let pendingBeats = [];
let stepCount = 0;
const cadence = createCadence(settings.bpm);
let frame = null;
let clockTimer = null;

/** Beats queued far enough ahead that the queue is worth draining eagerly. */
const PENDING_LIMIT = 256;

const engine = createMetronome({
  onBeats(beats) {
    pendingBeats.push(...beats);
    // The animation loop is what normally drains this, and the browser stops
    // running it while the page is hidden. On a long background run the queue
    // would otherwise grow for as long as the app is out of sight.
    if (pendingBeats.length > PENDING_LIMIT) drainPast();
  },
  // A tempo change throws away what was queued and hands the same beat numbers
  // out again. Dropping the originals keeps the step total honest; without
  // this, every step of a ramp counts its lookahead twice.
  onRevoke(fromCount) {
    pendingBeats = pendingBeats.filter((beat) => beat.count < fromCount);
  },
  onStateChange: paintStatus,
});

const keepAlive = createKeepAlive();
const wakeLock = createWakeLock(paintStatus);
const tapTempo = createTapTempo();
const mediaSession = createMediaSession({
  onPlay: () => start(),
  onStop: () => stop(),
});

// --- persistence ------------------------------------------------------------

function update(patch) {
  settings = { ...settings, ...patch };
  save(storage, settings);
}

// --- cadence ----------------------------------------------------------------

function setBpm(value, { fromRamp = false } = {}) {
  const applied = engine.setBpm(value);
  if (applied === cadence.current) return applied;
  // Only a deliberate change is saved. Persisting the ramp's own output would
  // overwrite the cadence you chose with the ramp's target, and the next run
  // would have nothing left to climb. See cadence.js.
  if (fromRamp) {
    cadence.sound(applied);
  } else {
    cadence.choose(applied);
    update({ bpm: cadence.chosen });
  }
  paintBpm();
  if (engine.running) mediaSession.describe(applied);
  return applied;
}

function paintBpm() {
  $('bpmOut').textContent = cadence.current;
  $('slider').value = cadence.current;
  presetGroup.setValue(cadence.current);

  const preset = PRESETS.find((entry) => entry.bpm === cadence.current);
  $('presetHint').textContent = preset
    ? `${preset.label}. ${preset.hint}.`
    : 'Set by hand. Tap a preset to jump to a known target.';
}

// --- start and stop ---------------------------------------------------------

async function start() {
  if (engine.running) return;

  cadence.beginRun();
  stepCount = 0;
  pendingBeats = [];

  // Ordering matters: the audio session type has to be set before the context
  // starts, and the keep-alive element has to be played inside the same user
  // gesture that opened it.
  engine.setBackgroundMode(settings.background);
  engine.start();
  if (settings.background) {
    await keepAlive.start();
    mediaSession.activate(cadence.current);
  }
  wakeLock.request();

  $('go').dataset.running = 'true';
  $('go').textContent = 'Stop';
  $('session').hidden = false;
  $('rampProgress').hidden = !settings.rampOn;
  announce(`Started at ${cadence.current} steps per minute`);

  startLoops();
  paintStatus();
}

function stop() {
  if (!engine.running) return;
  engine.stop();
  // Back to the cadence you chose, rather than sitting at wherever the ramp
  // finished, so starting again is not a surprise.
  cadence.endRun();
  paintBpm();
  keepAlive.stop();
  mediaSession.deactivate();
  wakeLock.release();
  stopLoops();

  $('go').dataset.running = 'false';
  $('go').textContent = 'Start';
  $('session').hidden = true;
  $('rampProgress').hidden = true;
  $('ring').classList.remove('tick', 'accent');
  announce('Stopped');
  paintStatus();
}

function announce(message) {
  $('live').textContent = message;
}

// --- animation and session clock --------------------------------------------

function startLoops() {
  if (frame === null) frame = requestAnimationFrame(pulse);
  if (clockTimer === null) clockTimer = setInterval(tickSession, 500);
}

function stopLoops() {
  if (frame !== null) cancelAnimationFrame(frame);
  if (clockTimer !== null) clearInterval(clockTimer);
  frame = null;
  clockTimer = null;
}

/**
 * Markers around the ring, one per beat in the accent cycle, so a glance tells
 * you where in the pattern you are. Nothing to draw when there is no cycle.
 */
function renderBeatDots() {
  const beats = $('beats');
  const count = settings.accentEvery;
  if (!count) {
    beats.replaceChildren();
    return;
  }
  const dots = Array.from({ length: count }, (unused, position) => {
    const dot = document.createElement('span');
    dot.className = 'beat-dot';
    dot.style.transform = `rotate(${(position / count) * 360}deg)`;
    dot.dataset.accent = String(position === 0);
    dot.dataset.on = 'false';
    return dot;
  });
  beats.replaceChildren(...dots);
}

function markBeatDot(beat) {
  const dots = $('beats').children;
  if (!dots.length) return;
  const position = beat.count % dots.length;
  for (let i = 0; i < dots.length; i += 1) dots[i].dataset.on = String(i === position);
}

/**
 * Retire every beat that has now sounded and report the last one, which is the
 * one worth showing. Counting happens here rather than in the animation loop so
 * the step total stays right while the page is hidden.
 */
function drainPast() {
  const now = engine.time;
  let last = null;
  while (pendingBeats.length && pendingBeats[0].time <= now) {
    last = pendingBeats.shift();
    stepCount += 1;
  }
  return last;
}

/** Flash the ring in time with the audio clock, not with a timer of its own. */
function pulse() {
  if (!engine.running) {
    frame = null;
    return;
  }
  const flashed = drainPast();
  if (flashed && !document.hidden) {
    const ring = $('ring');
    ring.classList.remove('tick', 'accent');
    void ring.offsetWidth; // restart the animation rather than let it continue
    ring.classList.add('tick');
    if (flashed.accent) ring.classList.add('accent');
    markBeatDot(flashed);
  }
  frame = requestAnimationFrame(pulse);
}

/** Session readout and the cadence ramp, both derived from elapsed time. */
function tickSession() {
  if (!engine.running) return;
  const elapsed = engine.elapsed;
  $('elapsed').textContent = formatClock(elapsed);
  $('steps').textContent = formatSteps(stepCount);

  if (!settings.rampOn) return;
  // Derived from elapsed time rather than accumulated, so a throttled timer in
  // the background changes nothing about where the ramp ends up.
  const target = cadence.rampTarget(settings.rampTo, settings.rampMinutes, elapsed);
  if (target !== cadence.current) setBpm(target, { fromRamp: true });

  const progress = rampProgress(settings.rampMinutes, elapsed);
  $('rampFill').style.transform = `scaleX(${progress})`;
  $('rampStatus').textContent = rampDone(settings.rampMinutes, elapsed)
    ? `Holding at ${settings.rampTo} steps per minute`
    : `Climbing to ${settings.rampTo} over ${settings.rampMinutes} min`;
}

// --- controls ---------------------------------------------------------------

const presetGroup = segmented(
  $('presets'),
  PRESETS.map((preset) => ({ value: preset.bpm, label: preset.label, sub: String(preset.bpm) })),
  (bpm) => setBpm(bpm),
);

const voiceGroup = segmented(
  $('voices'),
  VOICES.map((voice) => ({ value: voice.id, label: voice.label })),
  (id) => {
    update({ voice: engine.setVoice(id) });
    voiceGroup.setValue(settings.voice);
    $('voiceHint').textContent = findVoice(settings.voice).hint;
    // Audition it, so choosing a sound tells you what you picked.
    if (!engine.running && settings.click) engine.preview();
  },
);

const accentGroup = segmented(
  $('accents'),
  ACCENTS.map((accent) => ({ value: accent.every, label: accent.label })),
  (every) => {
    update({ accentEvery: engine.setAccentEvery(every) });
    accentGroup.setValue(settings.accentEvery);
    $('accentHint').textContent =
      ACCENTS.find((accent) => accent.every === settings.accentEvery)?.hint ?? '';
    renderBeatDots();
    if (!engine.running && settings.click) engine.preview({ accent: every > 0 });
  },
);

const clickSwitch = toggle({
  label: 'Click',
  hint: 'The sound you picked above',
  checked: settings.click,
  onChange(value) {
    update({ click: value });
    engine.setClick(value);
    paintOutputs();
  },
});

const vibrateSwitch = toggle({
  label: 'Vibrate',
  hint: capabilities.vibrate
    ? 'A pulse per step, silent to everyone else'
    : 'This browser does not offer vibration',
  checked: settings.vibrate && capabilities.vibrate,
  disabled: !capabilities.vibrate,
  onChange(value) {
    update({ vibrate: value });
    engine.setVibrate(value);
    paintOutputs();
  },
});

const backgroundSwitch = toggle({
  label: 'Keep playing in the background',
  hint: backgroundHint(settings.background),
  checked: settings.background,
  onChange(value) {
    update({ background: value });
    engine.setBackgroundMode(value);
    backgroundSwitch.set(value, { hint: backgroundHint(value) });
    if (engine.running) {
      if (value) {
        keepAlive.start().then(() => mediaSession.activate(cadence.current));
      } else {
        keepAlive.stop();
        mediaSession.deactivate();
      }
    }
    paintStatus();
  },
});

function backgroundHint(enabled) {
  if (enabled) {
    return isIOS()
      ? 'Carries on with the screen off. On iPhone this takes sole use of the speaker, so other players pause.'
      : 'Carries on with the screen off and through an app switch.';
  }
  return isIOS()
    ? 'Shares the speaker so your music keeps playing, but the beat stops once the screen locks.'
    : 'Gives your music priority. The beat may stop when the app is hidden.';
}

$('switches').append(clickSwitch.node, vibrateSwitch.node, backgroundSwitch.node);

const rampSwitch = toggle({
  label: 'Ramp the cadence',
  hint: 'Climb from where you start to the target',
  checked: settings.rampOn,
  onChange(value) {
    update({ rampOn: value });
    rampSwitch.set(value);
    $('rampFields').dataset.enabled = String(value);
    $('rampProgress').hidden = !(value && engine.running);
    if (value) cadence.rebaseRamp();
  },
});
$('rampSwitch').append(rampSwitch.node);

function paintOutputs() {
  // Whichever output is the last one standing gets locked on. Disabling it
  // says so up front, rather than letting the tap land and quietly do nothing.
  const onlyClick = settings.click && !settings.vibrate;
  const onlyVibrate = settings.vibrate && !settings.click;

  clickSwitch.set(settings.click, { disabled: onlyClick });
  vibrateSwitch.set(settings.vibrate && capabilities.vibrate, {
    disabled: !capabilities.vibrate || onlyVibrate,
  });

  // The volume slider means nothing when there is no sound to hear.
  $('volume').disabled = !settings.click;
  $('volume').closest('.row').dataset.enabled = String(settings.click);
}

// --- inputs -----------------------------------------------------------------

$('slider').addEventListener('input', (event) => setBpm(event.target.value));
$('dec').addEventListener('click', () => setBpm(cadence.current - 1));
$('inc').addEventListener('click', () => setBpm(cadence.current + 1));

$('volume').addEventListener('input', (event) => {
  const value = Number(event.target.value);
  update({ volume: value });
  engine.setVolume(value / 100);
  $('volumeValue').textContent = String(value);
});

$('rampTo').addEventListener('input', (event) => {
  update({ rampTo: Number(event.target.value) });
  $('rampToValue').textContent = String(settings.rampTo);
});

$('rampMinutes').addEventListener('input', (event) => {
  update({ rampMinutes: Number(event.target.value) });
  $('rampMinutesValue').textContent = `${settings.rampMinutes} min`;
});

$('tap').addEventListener('click', () => {
  const reading = tapTempo.tap(performance.now());
  if (reading.bpm === null) {
    $('tapHint').textContent = `Keep tapping, ${reading.taps} so far`;
    return;
  }
  setBpm(reading.bpm);
  $('tapHint').textContent = `Reading ${reading.bpm} steps per minute`;
});

$('go').addEventListener('click', () => (engine.running ? stop() : start()));

$('statusToggle').addEventListener('click', () => {
  const panel = $('statusPanel');
  const open = panel.hidden;
  panel.hidden = !open;
  $('statusToggle').setAttribute('aria-expanded', String(open));
  if (open) panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
});

// --- lifecycle --------------------------------------------------------------

document.addEventListener('visibilitychange', () => {
  engine.setHidden(document.hidden);
  if (document.hidden) return;
  engine.resume();
  // The system drops the wake lock whenever the page is hidden, so it has to
  // be taken again on the way back.
  if (engine.running) wakeLock.request();
  if (engine.running && frame === null) startLoops();
  paintStatus();
});

// iOS can hand the page back from the cache without a visibility change.
window.addEventListener('pageshow', () => {
  if (engine.running) engine.resume();
});

// --- status -----------------------------------------------------------------

function paintStatus() {
  const list = $('checks');
  if (!list) return;

  const state = engine.contextState;
  const rows = [
    settings.background
      ? check({
          tone: engine.running ? (keepAlive.playing ? 'good' : 'warn') : 'idle',
          title: keepAlive.playing ? 'Running in the background' : 'Background mode is on',
          detail: keepAlive.playing
            ? 'A media session is open, so the beat survives the screen locking and an app switch.'
            : 'The media session opens when you start.',
        })
      : check({
          tone: 'warn',
          title: 'Background mode is off',
          detail: 'Your music has priority. Expect the beat to stop once the app is hidden.',
        }),
    check({
      tone: capabilities.audioSession ? (settings.background ? 'warn' : 'good') : 'idle',
      title: capabilities.audioSession
        ? settings.background
          ? 'Your music will pause'
          : 'Mixing with your music'
        : 'Mixing is up to the browser',
      detail: capabilities.audioSession
        ? settings.background
          ? 'Safari gives background audio sole use of the output. Turn background mode off to share it.'
          : 'Clicks layer over Spotify. The ringer switch has to be on for you to hear them.'
        : 'Android has no mixing control on the web. Start your music first, then start here and listen.',
    }),
    check({
      tone: state === 'running' ? 'good' : state === 'idle' ? 'idle' : 'warn',
      title: `Audio engine ${state}`,
      detail:
        state === 'running'
          ? 'Beats are being placed on the audio clock ahead of time.'
          : state === 'idle'
            ? 'Starts on your first tap. Browsers require a tap before any sound.'
            : 'Another app took the output, or the browser paused it. Tap Start again.',
    }),
    check({
      tone: engine.usingWorkerPump ? 'good' : 'warn',
      title: engine.usingWorkerPump ? 'Timing runs off the main thread' : 'Timing runs on the main thread',
      detail: engine.usingWorkerPump
        ? 'A worker keeps the beat queue topped up even when the page is busy or hidden.'
        : 'Workers are unavailable here, so heavy pages could delay the queue.',
    }),
    check({
      tone: capabilities.vibrate ? 'good' : 'idle',
      title: capabilities.vibrate ? 'Vibration available' : 'No vibration on this browser',
      detail: capabilities.vibrate
        ? 'Usable on its own or alongside the sound.'
        : 'iPhone does not offer vibration to the web. Use a sound instead.',
    }),
    check({
      tone: wakeLock.supported ? (wakeLock.held ? 'good' : 'idle') : 'idle',
      title: wakeLock.supported
        ? wakeLock.held
          ? 'Screen staying awake'
          : 'Screen wake lock ready'
        : 'No wake lock on this browser',
      detail: wakeLock.supported
        ? 'Convenience only. The beat is built to carry on with the screen off.'
        : 'The screen will sleep on its own schedule. The beat carries on regardless.',
    }),
  ];
  list.replaceChildren(...rows);
}

// --- first paint ------------------------------------------------------------

function init() {
  $('slider').min = String(MIN_BPM);
  $('slider').max = String(MAX_BPM);
  $('rampTo').min = String(MIN_BPM);
  $('rampTo').max = String(MAX_BPM);

  engine.setBpm(cadence.current);
  engine.setVolume(settings.volume / 100);
  engine.setClick(settings.click);
  engine.setVibrate(settings.vibrate && capabilities.vibrate);
  engine.setVoice(settings.voice);
  engine.setAccentEvery(settings.accentEvery);
  engine.setBackgroundMode(settings.background);
  engine.setHidden(document.hidden);

  paintBpm();
  voiceGroup.setValue(settings.voice);
  accentGroup.setValue(settings.accentEvery);
  $('voiceHint').textContent = findVoice(settings.voice).hint;
  $('accentHint').textContent =
    ACCENTS.find((accent) => accent.every === settings.accentEvery)?.hint ?? '';

  $('volume').value = String(settings.volume);
  $('volumeValue').textContent = String(settings.volume);
  $('rampTo').value = String(settings.rampTo);
  $('rampToValue').textContent = String(settings.rampTo);
  $('rampMinutes').value = String(settings.rampMinutes);
  $('rampMinutesValue').textContent = `${settings.rampMinutes} min`;
  $('rampFields').dataset.enabled = String(settings.rampOn);
  renderBeatDots();

  paintOutputs();
  paintStatus();

  if (!capabilities.audio) {
    $('go').disabled = true;
    $('go').textContent = 'Web Audio unavailable';
  }
}

init();
