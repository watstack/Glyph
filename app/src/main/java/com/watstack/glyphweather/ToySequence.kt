package com.watstack.glyphweather

/** The weather the matrix shows: live from [WeatherRepository], or simulated from the app. */
data class WeatherState(val temperature: Double?, val condition: WeatherIcons.Condition?, val uv: Double?)

/**
 * What the Glyph Matrix shows, step by step. Used by the toy service to drive the real matrix and by the app's
 * preview, so the two always match. Pure Kotlin (no Android types) so it can be unit-tested on the JVM; the caller
 * owns the timer: draw [pixels], wait [delayMs], call [advance], repeat.
 *
 * Weather cycle: temperature for [temperatureMs] → animated icon (12 frames) → UV (after a sun, if known) → temperature.
 * A plane interrupts it: crossing (twice) → flight info scrolling [SCROLL_PASSES] times → altitude → back to the temperature.
 */
class ToySequence(private val live: () -> WeatherState, private val temperatureMs: Long = TEMPERATURE_MS) {
    enum class Phase { TEMPERATURE, ICON, UV, CROSSING, SCROLL, ALTITUDE }

    /** [TEMPERATURE], an icon frame 0 until [WeatherIcons.FRAMES], or [UV]. Ignored while a plane is showing. */
    private var frame = TEMPERATURE

    var flight: Flight? = null
        private set
    private var planeStep = 0
    private var scrollSteps = 0
    private val crossingSteps = FlightIcons.FRAMES * FlightIcons.CROSSINGS

    private var simulated: WeatherState? = null
    private var repeatSimulation = false

    /** The weather being shown: the simulated one while a simulation runs, otherwise live. */
    val weather: WeatherState get() = simulated ?: live()

    val isPlane: Boolean get() = flight != null

    val phase: Phase
        get() = when {
            flight != null && planeStep < crossingSteps -> Phase.CROSSING
            flight != null && planeStep < crossingSteps + scrollSteps -> Phase.SCROLL
            flight != null -> Phase.ALTITUDE
            frame == TEMPERATURE -> Phase.TEMPERATURE
            frame == UV -> Phase.UV
            else -> Phase.ICON
        }

    /** Back to the start of the weather cycle: the icon when [atIcon] and there is one, else the temperature. */
    fun restart(atIcon: Boolean) {
        flight = null
        frame = if (atIcon && weather.condition != null) 0 else TEMPERATURE
    }

    /** Interrupts with [f]: crossing, scrolling flight info, altitude. */
    fun startPlane(f: Flight, size: Int) {
        flight = f
        planeStep = 0
        scrollSteps = DigitFont.scrollSteps(f.ticker(), size) * SCROLL_PASSES
    }

    /** How long the current plane's whole sequence takes, in ms (0 when there's no plane). */
    fun planeDurationMs(): Long = if (flight == null) 0 else
        crossingSteps * FlightIcons.FRAME_MS + scrollSteps * SCROLL_MS + ALTITUDE_MS

    /**
     * Shows [state] instead of the live weather, from its icon: icon → UV (after a sun) → temperature.
     * Then live weather returns, unless [repeat], which keeps cycling it until [stopSimulation].
     */
    fun simulateWeather(state: WeatherState, repeat: Boolean) {
        simulated = state
        repeatSimulation = repeat
        flight = null
        frame = if (state.condition != null) 0 else TEMPERATURE
    }

    fun stopSimulation() {
        simulated = null
        repeatSimulation = false
    }

    fun advance() {
        if (flight != null) {
            planeStep++
            if (planeStep >= crossingSteps + scrollSteps + 1) {
                flight = null
                frame = TEMPERATURE
            }
            return
        }
        // A one-off simulation ends as its temperature screen finishes.
        if (frame == TEMPERATURE && simulated != null && !repeatSimulation) simulated = null
        val w = weather
        frame = when {
            w.condition == null -> TEMPERATURE
            frame == TEMPERATURE -> 0
            frame in 0 until WeatherIcons.FRAMES - 1 -> frame + 1
            frame == WeatherIcons.FRAMES - 1 && WeatherIcons.showsSun(w.condition) && w.uv != null -> UV
            else -> TEMPERATURE
        }
    }

    /** How long the current step stays up. */
    fun delayMs(): Long = when (phase) {
        Phase.TEMPERATURE -> temperatureMs
        Phase.ICON -> WeatherIcons.FRAME_MS
        Phase.UV -> WeatherIcons.UV_MS
        Phase.CROSSING -> FlightIcons.FRAME_MS
        Phase.SCROLL -> SCROLL_MS
        Phase.ALTITUDE -> ALTITUDE_MS
    }

    /** The current step on a [size]×[size] grid: row-major brightness, 0 = off. */
    fun pixels(size: Int): IntArray {
        val f = flight
        val w = weather
        return when (phase) {
            Phase.CROSSING -> FlightIcons.frame(f!!.trackDeg, planeStep % FlightIcons.FRAMES, size)
            Phase.SCROLL -> {
                // Each pass starts again with the text entering from the right.
                val onePass = scrollSteps / SCROLL_PASSES
                lit(DigitFont.renderScroll(f!!.ticker(), (planeStep - crossingSteps) % onePass, size))
            }
            Phase.ALTITUDE -> lit(DigitFont.renderAltitude(f!!.altitudeFt, size))
            Phase.UV -> lit(DigitFont.renderUv(w.uv, size))
            Phase.ICON -> w.condition?.let { WeatherIcons.frame(it, frame, size) }
                ?: lit(DigitFont.render(DigitFont.label(w.temperature), size))
            Phase.TEMPERATURE -> lit(DigitFont.render(DigitFont.label(w.temperature), size))
        }
    }

    private fun lit(px: BooleanArray) = IntArray(px.size) { if (px[it]) WeatherIcons.FULL else 0 }

    companion object {
        const val TEMPERATURE_MS = 30_000L
        const val SCROLL_MS = 80L
        const val ALTITUDE_MS = 3_000L
        /** How many times the flight info scrolls past for each plane. */
        const val SCROLL_PASSES = 4
        private const val TEMPERATURE = -1
        private const val UV = WeatherIcons.FRAMES
    }
}
