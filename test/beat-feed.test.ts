import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createBeatFeed, type FeedBeat } from '../src/core/beat-feed.ts';

function beat(count: number, time: number, accent = false): FeedBeat {
  return { count, time, accent };
}

test('draining counts each sounded beat once and reports the last', () => {
  const feed = createBeatFeed();
  feed.enqueue([beat(0, 1.0), beat(1, 1.5), beat(2, 2.0), beat(3, 2.5)]);

  const flashed = feed.drainPast(2.0);

  assert.equal(flashed?.count, 2, 'the most recent sounded beat is the one reported');
  assert.equal(feed.steps, 3, 'three beats have sounded');
  assert.equal(feed.pending, 1, 'the future beat is still queued');
});

test('draining with nothing due reports nothing and counts nothing', () => {
  const feed = createBeatFeed();
  feed.enqueue([beat(0, 5.0)]);

  assert.equal(feed.drainPast(1.0), null);
  assert.equal(feed.steps, 0);
  assert.equal(feed.pending, 1);
});

test('a revocation drops the stale beats so their numbers are not counted twice', () => {
  // The engine re-issues revoked numbers: after revokeFrom(2), beats 2 and 3
  // arrive again on the new grid. Without the drop, the step total counts the
  // lookahead twice -- the defect that motivated the onRevoke handler.
  const feed = createBeatFeed();
  feed.enqueue([beat(0, 1.0), beat(1, 1.5), beat(2, 2.0), beat(3, 2.5)]);
  feed.drainPast(1.6); // 0 and 1 have sounded

  feed.revokeFrom(2);
  feed.enqueue([beat(2, 2.1), beat(3, 2.7)]); // the re-planned replacements
  feed.drainPast(10);

  assert.equal(feed.steps, 4, 'beats 0..3 each counted exactly once');
  assert.equal(feed.pending, 0);
});

test('revocation never touches beats that already sounded', () => {
  const feed = createBeatFeed();
  feed.enqueue([beat(0, 1.0), beat(1, 1.5)]);
  feed.drainPast(2.0);

  feed.revokeFrom(0);

  assert.equal(feed.steps, 2, 'sounded beats stay counted; only pending ones are revocable');
});

test('reset starts a new run from zero', () => {
  const feed = createBeatFeed();
  feed.enqueue([beat(0, 1.0), beat(1, 1.5)]);
  feed.drainPast(2.0);

  feed.reset();

  assert.equal(feed.steps, 0);
  assert.equal(feed.pending, 0);
  assert.equal(feed.drainPast(10), null);
});
