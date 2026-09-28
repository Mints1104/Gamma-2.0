package com.mints.projectgammatwo.helpers

import java.math.BigDecimal

/**
 * A coordinate as plain decimal text, for anything another app has to parse — teleport links
 * and copied coordinates.
 *
 * Double.toString switches to scientific notation below 0.001, which turned spots near the
 * equator or the Greenwich meridian (a strip running through London) into "-7.47E-4". And
 * String.format uses the device locale, so "%.5f" wrote "40,77839" wherever the decimal
 * separator is a comma.
 */
fun Double.toPlainCoordinate(): String = BigDecimal.valueOf(this).toPlainString()

/** Whether [lat]/[lng] is a real position on Earth; false for NaN or infinity too. */
fun isValidLatLng(lat: Double, lng: Double): Boolean =
    lat in -90.0..90.0 && lng in -180.0..180.0
