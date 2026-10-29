package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeatFeedTest {
    private fun beat(count: Int, frame: Long, silent: Boolean = false) =
        CommittedBeat(frame = frame, count = count, accent = false, silent = silent)

    @Test
    fun `draining counts each sounded beat once and reports the last`() {
        val feed = BeatFeed()
        listOf(beat(0, 1000), beat(1, 1500), beat(2, 2000), beat(3, 2500)).forEach(feed::enqueue)

        val flashed = feed.drainPast(2000)

        assertEquals("the most recent sounded beat is the one reported", 2, flashed?.count)
        assertEquals("three beats have sounded", 3, feed.steps)
        assertEquals("the future beat is still queued", 1, feed.pending)
    }

    @Test
    fun `draining with nothing due reports nothing and counts nothing`() {
        val feed = BeatFeed()
        feed.enqueue(beat(0, 5000))

        assertNull(feed.drainPast(1000))
        assertEquals(0, feed.steps)
        assertEquals(1, feed.pending)
    }

    @Test
    fun `reset starts a new run from zero`() {
        val feed = BeatFeed()
        feed.enqueue(beat(0, 1000))
        feed.enqueue(beat(1, 1500))
        feed.drainPast(2000)

        feed.reset()

        assertEquals(0, feed.steps)
        assertEquals(0, feed.pending)
        assertNull(feed.drainPast(10_000))
    }

    @Test
    fun `an enqueued beat flagged silent is never counted`() {
        val feed = BeatFeed()
        listOf(beat(0, 1000), beat(1, 1500, silent = true), beat(2, 2000, silent = true), beat(3, 2500))
            .forEach(feed::enqueue)

        feed.drainPast(10_000)

        assertEquals(2, feed.steps)
        assertEquals(0, feed.pending)
    }

    @Test
    fun `a beat flagged silent is never the one drainPast returns to flash`() {
        val feed = BeatFeed()
        listOf(beat(0, 1000), beat(1, 1500, silent = true)).forEach(feed::enqueue)

        assertEquals("the last audible beat is the one to show", 0, feed.drainPast(2000)?.count)

        feed.enqueue(beat(2, 2500, silent = true))
        assertNull("a drain of only silent beats flashes nothing", feed.drainPast(3000))
    }
}
