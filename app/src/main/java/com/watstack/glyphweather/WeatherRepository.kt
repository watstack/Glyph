package com.watstack.glyphweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Fetches the current temperature and weather code from Open-Meteo (free, no API key) and caches it.
 * Blocking calls: run [refresh] off the main thread.
 */
class WeatherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("weather", Context.MODE_PRIVATE)

    var fahrenheit: Boolean
        get() = prefs.getBoolean(KEY_FAHRENHEIT, false)
        set(value) {
            // A cached value in the other unit is wrong now; force a refetch.
            prefs.edit().putBoolean(KEY_FAHRENHEIT, value).remove(KEY_FETCHED_AT).apply()
        }

    fun cachedTemperature(): Double? =
        if (prefs.contains(KEY_TEMP)) prefs.getFloat(KEY_TEMP, 0f).toDouble() else null

    /** The icon for the cached weather, or null if we don't have one yet. */
    fun cachedCondition(): WeatherIcons.Condition? =
        if (prefs.contains(KEY_CODE)) WeatherIcons.condition(prefs.getInt(KEY_CODE, -1), prefs.getBoolean(KEY_IS_DAY, true)) else null

    // A cache from before weather codes were fetched is stale too.
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        !prefs.contains(KEY_CODE) || now - prefs.getLong(KEY_FETCHED_AT, 0L) > MAX_AGE_MS

    fun hasLocation(): Boolean = prefs.contains(KEY_LAT)

    /** Remembers [location], rounded to ~1 km, for when a fresh fix isn't available. */
    fun saveLocation(location: Location) {
        prefs.edit()
            .putFloat(KEY_LAT, round2(location.latitude).toFloat())
            .putFloat(KEY_LON, round2(location.longitude).toFloat())
            .apply()
    }

    /** Updates the cached temperature and weather. Returns true on success. */
    fun refresh(): Boolean {
        lastKnownLocation()?.let(::saveLocation)
        if (!hasLocation()) return false
        val lat = prefs.getFloat(KEY_LAT, 0f)
        val lon = prefs.getFloat(KEY_LON, 0f)
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&temperature_unit=%s",
                lat, lon, unit
            )
        )
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val current = JSONObject(body).getJSONObject("current")
            prefs.edit()
                .putFloat(KEY_TEMP, current.getDouble("temperature_2m").toFloat())
                .putInt(KEY_CODE, current.getInt("weather_code"))
                .putBoolean(KEY_IS_DAY, current.optInt("is_day", 1) == 1)
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            false
        }
    }

    /** The phone's last known approximate position, if permission allows. */
    private fun lastKnownLocation(): Location? {
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        return try {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            // Android may refuse location to a background process; the saved location is used instead.
            null
        }
    }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    companion object {
        private const val TAG = "GlyphWeather"
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val KEY_TEMP = "temp"
        private const val KEY_CODE = "weather_code"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_FAHRENHEIT = "fahrenheit"
    }
}
