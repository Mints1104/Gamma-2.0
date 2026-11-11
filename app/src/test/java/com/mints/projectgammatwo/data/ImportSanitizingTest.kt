package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.DEFAULT_BUTTON_ORDER
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.DEFAULT_BUTTON_SIZE
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.MAX_BUTTON_SIZE
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.MIN_BUTTON_SIZE
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.sanitizeButtonOrder
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.sanitizeButtonSize
import com.mints.projectgammatwo.data.OverlayCustomizationManager.Companion.sanitizeButtonVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a settings import (or an older import's leftovers) can store for the overlay and sources. */
class ImportSanitizingTest {

    @Test
    fun `button size is clamped to the slider range`() {
        assertEquals(MAX_BUTTON_SIZE, sanitizeButtonSize(5000))
        assertEquals(MIN_BUTTON_SIZE, sanitizeButtonSize(0))
        assertEquals(40, sanitizeButtonSize(40))
        assertEquals(DEFAULT_BUTTON_SIZE, sanitizeButtonSize(null))
    }

    @Test
    fun `an empty or missing button order becomes the default`() {
        assertEquals(DEFAULT_BUTTON_ORDER, sanitizeButtonOrder(emptyList()))
        assertEquals(DEFAULT_BUTTON_ORDER, sanitizeButtonOrder(null))
    }

    @Test
    fun `a custom order is kept, cleaned up and completed`() {
        val order = sanitizeButtonOrder(listOf("home_button", "bogus", null, "home_button", "drag_handle", "close_button"))

        assertEquals(listOf("drag_handle", "home_button", "close_button"), order.take(3))
        assertEquals(DEFAULT_BUTTON_ORDER.toSet(), order.toSet())
        assertEquals(DEFAULT_BUTTON_ORDER.size, order.size)
    }

    @Test
    fun `visibility covers every button and can't hide the essential ones`() {
        val visibility = sanitizeButtonVisibility(
            mapOf("close_button" to false, "drag_handle" to false, "home_button" to false, "bogus" to false)
        )

        assertEquals(DEFAULT_BUTTON_ORDER.toSet(), visibility.keys)
        assertEquals(false, visibility["home_button"])
        assertEquals(true, visibility["close_button"])
        assertEquals(true, visibility["drag_handle"])
        assertTrue(visibility.filterKeys { it !in setOf("home_button") }.values.all { it })
    }

    @Test
    fun `only known data sources are imported, and none means keep the current ones`() {
        val known = setOf("NYC", "LONDON")
        assertEquals(setOf("LONDON"), DataSourcePreferences.sanitizeSources(setOf("LONDON", "PARIS"), known))
        assertNull(DataSourcePreferences.sanitizeSources(emptySet(), known))
        assertNull(DataSourcePreferences.sanitizeSources(setOf("PARIS"), known))
        assertNull(DataSourcePreferences.sanitizeSources(null, known))
    }
}
