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
 * Fetches the current temperature, weather code and UV index from Open-Meteo (free, no API key) and caches it.
 * Blocking calls: run [refresh] off the main thread.
 *
 * Also keeps the phone's location. The precise fix stays on the phone (plane distances use it); only a point
 * rounded to ~1 km ([sharedLocation]) is sent to Open-Meteo and the flight feeds.
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

    /** The cached UV index, or null if we don't have one yet. */
    fun cachedUv(): Double? =
        if (prefs.contains(KEY_UV)) prefs.getFloat(KEY_UV, 0f).toDouble() else null

    // A cache from an older version (before weather codes or UV were fetched) is stale too.
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        prefs.getInt(KEY_FORMAT, 0) < FORMAT || isOlderThan(MAX_AGE_MS, now)

    /** Whether the cache was fetched more than [ms] ago (or never). */
    fun isOlderThan(ms: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - prefs.getLong(KEY_FETCHED_AT, 0L) > ms

    fun hasLocation(): Boolean = prefs.contains(KEY_PRECISE_LAT) || prefs.contains(KEY_LAT)

    /** Whether the user allowed precise location (not just approximate). */
    fun preciseAllowed(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The saved fix, or null before the first one. Installs from before precise location have only a rounded point. */
    fun savedFix(): Fix? = when {
        prefs.contains(KEY_PRECISE_LAT) -> Fix(
            Double.fromBits(prefs.getLong(KEY_PRECISE_LAT, 0L)),
            Double.fromBits(prefs.getLong(KEY_PRECISE_LON, 0L)),
            if (prefs.contains(KEY_ACCURACY)) prefs.getFloat(KEY_ACCURACY, 0f) else null,
            prefs.getLong(KEY_FIX_AT, 0L),
        )
        prefs.contains(KEY_LAT) ->
            Fix(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LON, 0f).toDouble(), null, 0L)
        else -> null
    }

    /** The saved latitude and longitude at full precision, for use on the phone only. */
    fun savedLocation(): Pair<Double, Double>? = savedFix()?.let { it.lat to it.lon }

    /** The saved location rounded to ~1 km: the only form sent to online services. */
    fun sharedLocation(): Pair<Double, Double>? =
        savedFix()?.let { LocationPick.round2(it.lat) to LocationPick.round2(it.lon) }

    /** Remembers [location] for when a fresh fix isn't available. */
    fun saveLocation(location: Location) {
        saveFix(Fix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null, location.time))
    }

    private fun saveFix(fix: Fix) {
        val edit = prefs.edit()
            .putLong(KEY_PRECISE_LAT, fix.lat.toRawBits())
            .putLong(KEY_PRECISE_LON, fix.lon.toRawBits())
            .putLong(KEY_FIX_AT, fix.timeMs)
            // Keep the rounded copy too, so a downgrade to an older version still finds a location.
            .putFloat(KEY_LAT, LocationPick.round2(fix.lat).toFloat())
            .putFloat(KEY_LON, LocationPick.round2(fix.lon).toFloat())
        if (fix.accuracyM != null) edit.putFloat(KEY_ACCURACY, fix.accuracyM) else edit.remove(KEY_ACCURACY)
        edit.apply()
    }

    /** Takes the best recent fix the phone already has, if it beats the saved one. */
    fun updateFromLastKnown() {
        val saved = savedFix()
        val best = LocationPick.best(lastKnownFixes() + listOfNotNull(saved), System.currentTimeMillis())
        if (best != null && best != saved) saveFix(best)
    }

    /** Updates the cached temperature and weather. Returns true on success. */
    fun refresh(): Boolean {
        updateFromLastKnown()
        val (lat, lon) = sharedLocation() ?: return false
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day,uv_index&temperature_unit=%s",
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
            val edit = prefs.edit()
                .putFloat(KEY_TEMP, current.getDouble("temperature_2m").toFloat())
                .putInt(KEY_CODE, current.getInt("weather_code"))
                .putBoolean(KEY_IS_DAY, current.optInt("is_day", 1) == 1)
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .putInt(KEY_FORMAT, FORMAT)
            // uv_index can be null in the response; keep the last value rather than store a wrong 0.
            val uv = current.optDouble("uv_index", Double.NaN)
            if (!uv.isNaN()) edit.putFloat(KEY_UV, uv.toFloat())
            edit.apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            false
        }
    }

    /** The fixes the phone already has from each provider (no new fix is requested), if permission allows. */
    private fun lastKnownFixes(): List<Fix> {
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return emptyList()
        return try {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .map { Fix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null, it.time) }
        } catch (e: SecurityException) {
            // Android may refuse location to a background process; the saved location is used instead.
            emptyList()
        }
    }

    companion object {
        private const val TAG = "GlyphWeather"
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val KEY_TEMP = "temp"
        private const val KEY_CODE = "weather_code"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_UV = "uv_index"
        /** Bumped when a fetch starts storing new fields, so older caches refetch once. 2 = with UV. */
        private const val KEY_FORMAT = "format"
        private const val FORMAT = 2
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_PRECISE_LAT = "precise_lat"
        private const val KEY_PRECISE_LON = "precise_lon"
        private const val KEY_ACCURACY = "accuracy_m"
        private const val KEY_FIX_AT = "fix_at"
        private const val KEY_FAHRENHEIT = "fahrenheit"
    }
}
