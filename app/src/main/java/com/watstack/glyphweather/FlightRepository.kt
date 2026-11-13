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
    private val routes = HashMap<String, Triple<String?, String?, String?>?>()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** The nearest plane within [Flights.RADIUS_KM], with its route if known, or null. */
    fun nearest(): Flight? {
        val (lat, lon) = weather.savedLocation() ?: return null
        val path = String.format(Locale.US, "/v2/point/%.2f/%.2f/%d", lat, lon, RADIUS_NM)
        val body = get("https://api.adsb.lol$path") ?: get("https://api.airplanes.live$path") ?: return null
        val flight = try {
            Flights.nearest(body, lat, lon)
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable flight data", e)
            null
        } ?: return null
        return withRoute(flight).also(::remember)
    }

    /** The last plane shown, for the app screen: e.g. "QF401 MEL>SYD B738 · 35K ft · 2.4 km", and when. */
    fun lastSeen(): Pair<String, Long>? {
        val text = prefs.getString(KEY_LAST, null) ?: return null
        return text to prefs.getLong(KEY_LAST_AT, 0L)
    }

    private fun withRoute(f: Flight): Flight {
        val cs = f.callsign ?: return f
        val route = routes.getOrPut(cs) {
            get("https://api.adsbdb.com/v0/callsign/" + URLEncoder.encode(cs, "UTF-8"))?.let {
                runCatching { Flights.route(it) }.getOrNull()
            }
        } ?: return f
        return f.copy(flightNumber = route.first, from = route.second, to = route.third)
    }

    private fun remember(f: Flight) {
        val text = String.format(Locale.US, "%s · %s ft · %.1f km", f.ticker(), DigitFont.altitudeLabel(f.altitudeFt), f.distanceKm)
        prefs.edit().putString(KEY_LAST, text).putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
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
        /** Search radius for the feed, in nautical miles; just over 5 km so nothing near the edge is missed. */
        private const val RADIUS_NM = 3
        private const val TIMEOUT_MS = 8_000
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last"
        private const val KEY_LAST_AT = "last_at"
    }
}
