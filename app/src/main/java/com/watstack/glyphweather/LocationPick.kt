package com.watstack.glyphweather

/** A location fix: where, how accurate (metres, null if unknown) and when (wall-clock ms). */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Float?, val timeMs: Long)

/**
 * Choosing which location to trust, and rounding what we share. Pure Kotlin (no Android types) so it can be
 * unit-tested on the JVM.
 */
object LocationPick {
    /** Fixes older than this aren't trusted: the phone may have moved since. */
    const val MAX_AGE_MS = 30 * 60 * 1000L
    /** Fixes vaguer than this (approximate location can be ~2 km, cell-only fixes worse) aren't trusted. */
    const val MAX_ACCURACY_M = 3_000f

    /** The most accurate of [fixes] that is recent and accurate enough, or null if none is. */
    fun best(fixes: List<Fix>, now: Long): Fix? = fixes
        .filter { now - it.timeMs in 0..MAX_AGE_MS && (it.accuracyM ?: Float.MAX_VALUE) <= MAX_ACCURACY_M }
        .minByOrNull { it.accuracyM ?: Float.MAX_VALUE }

    /** Rounds to 2 decimal places (~1 km): all that is ever sent to the weather and flight services. */
    fun round2(v: Double): Double = Math.round(v * 100) / 100.0
}
