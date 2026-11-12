package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBoundsTest {

    @Test
    fun `a start-anchored window moves between the edges`() {
        assertEquals(0..900, onScreenOffsetRange(screen = 1080, view = 180, centered = false))
    }

    @Test
    fun `a centred window moves the same distance either side of the centre`() {
        // The favorites and filter panels: a 0..900 clamp let them slide 450 px off the right.
        assertEquals(-450..450, onScreenOffsetRange(screen = 1080, view = 180, centered = true))
    }

    @Test
    fun `a window larger than the screen gets one position, not an empty range`() {
        // coerceIn(0, -120) threw IllegalArgumentException.
        assertEquals(0..0, onScreenOffsetRange(screen = 1080, view = 1200, centered = false))
        assertEquals(0..0, onScreenOffsetRange(screen = 1080, view = 1200, centered = true))
        assertEquals(0, 500.coerceIn(onScreenOffsetRange(1080, 1200, centered = false)))
    }
}
