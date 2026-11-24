package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {

    @Test
    fun `small coordinates are written as plain decimals`() {
        // Double.toString gives "-7.47E-4", which teleport apps can't parse.
        assertEquals("-0.000747", (-0.000747).toPlainCoordinate())
        assertEquals("51.5074", 51.5074.toPlainCoordinate())
        assertEquals("0.0", 0.0.toPlainCoordinate())
    }

    @Test
    fun `only real positions are valid`() {
        assertTrue(isValidLatLng(90.0, -180.0))
        assertTrue(isValidLatLng(-90.0, 180.0))
        assertFalse(isValidLatLng(407.128, -73.9))
        assertFalse(isValidLatLng(40.7, -273.9))
        assertFalse(isValidLatLng(Double.NaN, 0.0))
        assertFalse(isValidLatLng(0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `typed coordinates parse with or without spaces`() {
        assertEquals(40.7128 to -74.006, parseLatLng("40.7128, -74.0060"))
        assertEquals(40.7128 to -74.006, parseLatLng(" 40.7128,-74.0060 "))
        assertEquals(-0.000747 to 0.0, parseLatLng("-0.000747, 0"))
    }

    @Test
    fun `malformed or out-of-range coordinates don't parse`() {
        assertNull(parseLatLng(null))
        assertNull(parseLatLng(""))
        assertNull(parseLatLng("40.7128"))
        assertNull(parseLatLng("40,7128, -74,0060"))
        assertNull(parseLatLng("abc, def"))
        assertNull(parseLatLng("407.128, -74.006"))
    }
}
