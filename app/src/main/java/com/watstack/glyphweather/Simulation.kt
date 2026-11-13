package com.watstack.glyphweather

/**
 * Hands a simulation from the app to the toy service. Both run in the app's process, on the main thread.
 * A request waits up to [MAX_AGE_MS] for the phone to go face down; if the toy is already showing, it plays at once.
 */
object Simulation {
    sealed interface Request
    data class Weather(val state: WeatherState) : Request
    data class Plane(val flight: Flight) : Request

    const val MAX_AGE_MS = 60_000L

    private var pending: Request? = null
    private var sentAt = 0L

    /** Set by the toy service while it's bound. Returns true if it played the request now. */
    var listener: ((Request) -> Boolean)? = null

    /** Sends [request] to the matrix. Returns true if it played now, false if it waits for the phone to go face down. */
    fun send(request: Request, now: Long): Boolean {
        if (listener?.invoke(request) == true) {
            pending = null
            return true
        }
        pending = request
        sentAt = now
        return false
    }

    /** The waiting request, if it's recent enough, and clears it. */
    fun take(now: Long): Request? {
        val r = pending ?: return null
        pending = null
        return if (now - sentAt <= MAX_AGE_MS) r else null
    }
}
