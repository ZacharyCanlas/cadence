package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatGridTest {
    @Test
    fun `clampBpm holds the tempo inside the runnable range`() {
        assertEquals(180, clampBpm(180.0))
        assertEquals(MIN_BPM, clampBpm(10.0))
        assertEquals(MAX_BPM, clampBpm(9999.0))
        assertEquals(180, clampBpm(179.6))
    }

    @Test
    fun `clampBpm falls back to the default on junk input`() {
        assertEquals(180, clampBpm(Double.NaN))
        assertEquals(180, clampBpm(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `beatPeriod matches the tempo`() {
        assertEquals(0.5, beatPeriod(120), 0.0)
        assertEquals(1.0 / 3, beatPeriod(180), 0.0)
    }
}
