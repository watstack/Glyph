package com.watstack.glyphweather

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Finds the plane overhead from free ADS-B feeds (adsb.lol, falling back to airplanes.live; no API key) and looks up
 * its route on adsbdb.com. Uses the same rounded location as the weather. Blocking calls: run [nearest] off the main thread.
 */
class FlightRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("flights", Context.MODE_PRIVATE)
    private val weather = WeatherRepository(context)
    /** Route per callsign, null when adsbdb doesn't know it. Kept for the life of the process. */
    private val routes = HashMap<String, Route?>()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** The nearest plane within [Flights.RADIUS_KM], with its route if known, or null. Remembered for the app screen. */
    fun nearest(): Flight? = find(Flights.RADIUS_KM).flight?.also(::remember)

    /** Result of a lookup: the nearest plane (or null), and whether the feed could be reached at all. */
    data class Lookup(val flight: Flight?, val reached: Boolean, val hasLocation: Boolean = true)

    /** The nearest airborne plane within [radiusKm], with its route if known. */
    fun find(radiusKm: Double): Lookup {
        // Ask the feed around the rounded point (widened to cover the rounding); measure from the precise one.
        val (lat, lon) = weather.savedLocation() ?: return Lookup(null, reached = false, hasLocation = false)
        val (sharedLat, sharedLon) = weather.sharedLocation() ?: return Lookup(null, reached = false, hasLocation = false)
        val nm = Math.ceil((radiusKm + ROUNDING_KM) / KM_PER_NM).toInt().coerceIn(1, MAX_RADIUS_NM)
        val path = String.format(Locale.US, "/v2/point/%.2f/%.2f/%d", sharedLat, sharedLon, nm)
        val body = get("https://api.adsb.lol$path") ?: get("https://api.airplanes.live$path")
            ?: return Lookup(null, reached = false)
        val flight = try {
            Flights.nearest(body, lat, lon, radiusKm)
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable flight data", e)
            return Lookup(null, reached = false)
        }
        return Lookup(flight?.let(::withRoute), reached = true)
    }

    /** The same segments the matrix shows, then the altitude: "QF401 - MEL>SYD - B738 - Qantas - 830 km/h - 35K". */
    fun describe(f: Flight): String = (f.segments() + DigitFont.altitudeLabel(f.altitudeFt)).joinToString(" - ")

    /** Context the matrix doesn't show: "2.3 km away". */
    fun distance(f: Flight): String = String.format(Locale.US, "%.1f km away", f.distanceKm)

    /** The last plane shown, for the app screen: its [describe] line, its [distance], and when it was seen. */
    fun lastSeen(): Triple<String, String, Long>? {
        val text = prefs.getString(KEY_LAST, null) ?: return null
        // Older versions stored one combined line; show it as it is.
        return Triple(text, prefs.getString(KEY_LAST_DISTANCE, null) ?: "", prefs.getLong(KEY_LAST_AT, 0L))
    }

    private fun withRoute(f: Flight): Flight {
        val cs = f.callsign ?: return f
        val route = routes.getOrPut(cs) {
            get("https://api.adsbdb.com/v0/callsign/" + URLEncoder.encode(cs, "UTF-8"))?.let {
                runCatching { Flights.route(it) }.getOrNull()
            }
        } ?: return f
        return f.copy(flightNumber = route.flightNumber, from = route.from, to = route.to, airline = route.airline)
    }

    private fun remember(f: Flight) {
        prefs.edit()
            .putString(KEY_LAST, describe(f))
            .putString(KEY_LAST_DISTANCE, distance(f))
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    private fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "GlyphSky/1.0")
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Fetch failed: $url", e)
        null
    }

    companion object {
        private const val TAG = "GlyphSky"
        private const val KM_PER_NM = 1.852
        /** Most a 2-decimal rounding can move a point: half of 0.01° on each axis, about 0.8 km. */
        private const val ROUNDING_KM = 1.0
        /** The feeds' largest search radius. */
        private const val MAX_RADIUS_NM = 250
        private const val TIMEOUT_MS = 8_000
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_DISTANCE = "last_distance"
    }
}
