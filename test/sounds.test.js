import assert from 'node:assert/strict';
import { test } from 'node:test';

import { VOICES, findVoice, voiceIds } from '../src/sounds.js';
import { createFakeContext } from './helpers/audio-harness.js';

/** Every node reachable from `node`, following connections outward. */
function reaches(node, target, seen = new Set()) {
  if (node === target) return true;
  if (seen.has(node)) return false;
  seen.add(node);
  return node.outputs.some((next) => reaches(next, target, seen));
}

for (const voice of VOICES) {
  for (const accent of [false, true]) {
    const label = accent ? `${voice.id} (accent)` : voice.id;

    test(`${label} reaches the destination it was handed`, () => {
      const context = createFakeContext();
      const destination = context.createGain();
      const before = context.nodes.length;

      voice.play(context, destination, 5, { accent });

      const built = context.nodes.slice(before).filter((node) => node !== destination);
      assert.ok(built.length > 0, 'the voice built something');
      assert.ok(
        built.some((node) => reaches(node, destination)),
        'at least one path arrives at the destination',
      );
    });

    test(`${label} schedules itself at the time it was given, not the clock`, () => {
      // The whole point of placing beats seconds ahead is that a voice sounds
      // the same whenever it was queued. A voice reading context.currentTime
      // would fire at the wrong moment for everything queued in advance.
      const context = createFakeContext();
      context._ms = 1000; // a clock that is nowhere near the requested time
      const destination = context.createGain();
      const when = 60;

      voice.play(context, destination, when, { accent });

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
    });
  }
}

test('every voice is reachable by id, and an unknown id still returns one', () => {
  for (const id of voiceIds()) {
    assert.equal(findVoice(id).id, id);
  }
  assert.equal(findVoice('nonsense').id, 'click');
  assert.equal(findVoice(undefined).id, 'click');
});
