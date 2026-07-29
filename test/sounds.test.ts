import assert from 'node:assert/strict';
import { test } from 'node:test';

import { VOICES, findVoice, voiceIds, type Voice, type VoicePlayOptions } from '../src/core/sounds.ts';
import { createFakeContext, type FakeAudioContext, type FakeNode } from './helpers/audio-harness.ts';

/** Every node reachable from `node`, following connections outward. */
function reaches(node: FakeNode, target: FakeNode, seen = new Set<FakeNode>()): boolean {
  if (node === target) return true;
  if (seen.has(node)) return false;
  seen.add(node);
  return node.outputs.some((next) => reaches(next, target, seen));
}

/** The automation parameters a fake node can carry. */
const PARAMS = ['gain', 'frequency', 'Q'] as const;

/** Every automation call made on a node, as [kind, value, time]. */
function automation(node: FakeNode) {
  return PARAMS.flatMap((name) => node[name]?.events ?? []);
}

/** Hand the fake graph to a voice; the fakes are structural stand-ins for the real types. */
function play(
  voice: Voice,
  context: FakeAudioContext,
  destination: FakeNode,
  time: number,
  options?: VoicePlayOptions,
) {
  voice.play(context as unknown as AudioContext, destination as unknown as AudioNode, time, options);
}

for (const voice of VOICES) {
  for (const accent of [false, true]) {
    const label = accent ? `${voice.id} (accent)` : voice.id;

    test(`${label} reaches the destination it was handed`, () => {
      const context = createFakeContext();
      const destination = context.createGain();
      const before = context.nodes.length;

      play(voice, context, destination, 5, { accent });

      const built = context.nodes.slice(before).filter((node) => node !== destination);
      assert.ok(built.length > 0, 'the voice built something');
      // Every node, not merely one of them: a voice that wires its tone up
      // correctly and leaves its noise layer dangling is half silent, and a
      // check for any single path would not notice.
      for (const node of built) {
        assert.ok(reaches(node, destination), `the ${node.kind} arrives at the destination`);
      }
    });

    test(`${label} schedules itself at the time it was given, not the clock`, () => {
      // The whole point of placing beats seconds ahead is that a voice sounds
      // the same whenever it was queued. A voice reading context.currentTime
      // would fire at the wrong moment for everything queued in advance.
      const context = createFakeContext();
      context._ms = 1000; // a clock that is nowhere near the requested time
      const destination = context.createGain();
      const when = 60;

      play(voice, context, destination, when, { accent });

      const started = context.nodes.filter((node) => node.startedAt !== null);
      assert.ok(started.length > 0, 'something was scheduled');
      for (const node of started) {
        assert.equal(node.startedAt, when, `${node.kind} starts at the requested time`);
      }
      for (const node of context.nodes) {
        if (node.stoppedAt !== null) {
          assert.ok(node.stoppedAt > when, `${node.kind} stops after it starts`);
        }
      }

      // start() is only half the story. The envelopes and frequency sweeps are
      // the other half, and for the hi-hat the envelope is the only shaping
      // there is, so a voice reading the clock for its automation would be
      // wholly broken and still pass a check that only looked at start().
      const events = context.nodes.flatMap(automation);
      assert.ok(events.length > 0, 'the voice automates something');
      for (const [kind, , time] of events) {
        assert.ok(
          time >= when,
          `a ${kind} is scheduled at ${time}, before the beat at ${when} ` +
            `(the clock reads ${context.currentTime})`,
        );
      }
    });
  }
}

/**
 * What a render does to pitch and to level, separately.
 *
 * Kept apart on purpose. Comparing whole renders only catches an accent that
 * has been removed outright: with the re-pitching gone the levels still differ,
 * so the two renders still differ and the check passes while the accent no
 * longer cuts through. Both halves have to be pinned on their own.
 *
 * Parameters are read by value as well as by automation event, since some
 * voices set a filter frequency outright rather than scheduling it.
 */
function profile(voice: Voice, accent: boolean) {
  const context = createFakeContext();
  const destination = context.createGain();
  play(voice, context, destination, 5, { accent });
  const built = context.nodes.filter((node) => node !== destination);
  return {
    pitch: JSON.stringify(
      built.map((node) => [
        node.kind,
        node.type ?? null,
        node.frequency?.value ?? null,
        node.frequency?.events ?? [],
      ]),
    ),
    level: JSON.stringify(
      built.map((node) => [node.kind, node.gain?.value ?? null, node.gain?.events ?? []]),
    ),
  };
}

for (const voice of VOICES) {
  test(`${voice.id} is both re-pitched and lifted on the accent`, () => {
    // Sixteen tests parameterised by `accent` prove nothing about the accent if
    // none of them ever compares the two renders.
    const plain = profile(voice, false);
    const accented = profile(voice, true);
    assert.notEqual(plain.pitch, accented.pitch, 'the accent moves the pitch');
    assert.notEqual(plain.level, accented.level, 'the accent moves the level');
  });
}

test('every voice is reachable by id, and an unknown id still returns one', () => {
  for (const id of voiceIds()) {
    assert.equal(findVoice(id).id, id);
  }
  assert.equal(findVoice('nonsense').id, 'click');
  assert.equal(findVoice(undefined as unknown as string).id, 'click');
});
