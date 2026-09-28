package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
