package com.mints.projectgammatwo.data

import android.content.Context
import android.text.format.DateFormat
import com.mints.projectgammatwo.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Resolves a favorite's coordinates to an IANA timezone (offline, via [TimezoneMapper]) and
 * formats the current local time there, e.g. "14:32 · +8h".
 *
 * The only place in the app that touches [TimezoneMapper] and java.time, so both the Favorites
 * tab and the overlay favorites panel render identically.
 */
object FavoriteTimeFormatter {

    /**
     * [TimezoneMapper] returns this literal (index 0 of its table) when no polygon matches.
     * Also stored on a favorite to mean "we looked, there is no zone here" — distinct from a
     * null timezoneId, which means "not looked up yet".
     */
    const val UNKNOWN_ZONE = "unknown"

    private val zoneCache = ConcurrentHashMap<String, ZoneId>()

    /**
     * Maps coordinates to an IANA zone id, or null when the mapper has no match (ocean, poles)
     * or hands back an id the platform tzdb doesn't know.
     */
    fun resolveZoneId(lat: Double, lng: Double): String? {
        val id = try {
            TimezoneMapper.latLngToTimezoneString(lat, lng)
        } catch (e: Exception) {
            null
        }
        if (id.isNullOrBlank() || id == UNKNOWN_ZONE) return null
        return if (zoneOrNull(id) != null) id else null
    }

    /**
     * Current local time at [zoneId], suffixed with the offset from this device when they differ
     * (e.g. "14:32 · +8h", "09:17 AM · -5h30m"). Null when [zoneId] is null or unusable, in which
     * case callers should hide the time row rather than fall back to device time.
     */
    fun formatLocalTime(context: Context, zoneId: String?): String? {
        if (zoneId == null || zoneId == UNKNOWN_ZONE) return null
        val zone = zoneOrNull(zoneId) ?: return null
        val now = Instant.now()
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        val time = ZonedDateTime.ofInstant(now, zone)
            .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

        val offset = offsetLabel(zone, now) ?: return time
        return context.getString(R.string.favorite_local_time, time, offset)
    }

    /**
     * How far ahead/behind [zone] is from the device right now, or null when they currently
     * match. Computed from the instant rather than a fixed offset so DST on either side is
     * accounted for, and rendered with minutes because zones like Kathmandu (+5h45m) and
     * Chatham exist.
     */
    private fun offsetLabel(zone: ZoneId, now: Instant): String? {
        val deviceOffset = ZoneId.systemDefault().rules.getOffset(now).totalSeconds
        val zoneOffset = zone.rules.getOffset(now).totalSeconds
        val diff = zoneOffset - deviceOffset
        if (diff == 0) return null

        val sign = if (diff > 0) "+" else "-"
        val totalMinutes = abs(diff) / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            minutes == 0 -> "$sign${hours}h"
            hours == 0 -> "$sign${minutes}m"
            else -> "$sign${hours}h${minutes}m"
        }
    }

    private fun zoneOrNull(id: String): ZoneId? = zoneCache[id] ?: try {
        ZoneId.of(id).also { zoneCache[id] = it }
    } catch (e: Exception) {
        null
    }
}
