package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `the clock reads minutes and zero-padded seconds`() {
        assertEquals("0:00", formatClock(0.0))
        assertEquals("0:07", formatClock(7.9))
        assertEquals("1:05", formatClock(65.0))
        assertEquals("0:00", formatClock(-3.0))
    }

    @Test
    fun `the clock keeps counting minutes past the hour`() {
        assertEquals("75:12", formatClock(75 * 60 + 12.4))
    }

    @Test
    fun `counts are grouped in thousands`() {
        assertEquals("1", formatCount(1))
        assertEquals("999", formatCount(999))
        assertEquals("12,431", formatCount(12_431))
    }
}
