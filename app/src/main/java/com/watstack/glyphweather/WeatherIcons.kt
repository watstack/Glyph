package com.watstack.glyphweather

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Animated weather icons for the Glyph Matrix. Drawn for the 13×13 Phone (4a) Pro and centred on larger matrices.
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 *
 * Styled after Nothing's Moon Phase toy: shapes are shaded across many brightness levels, everything the animation
 * can light stays faintly visible (the "dark side"), and each icon opens with a short sweep before it loops.
 */
object WeatherIcons {
    /** 8 frames per second. */
    const val FRAME_MS = 125L
    /** 1 s intro, then a 4 s loop: [ICON_FRAMES] × [FRAME_MS] = 5 s. */
    const val INTRO_FRAMES = 8
    const val LOOP_FRAMES = 32
    const val ICON_FRAMES = INTRO_FRAMES + LOOP_FRAMES

    const val FULL = 255
    /** The faint glow of whatever isn't lit right now, like the moon's dark side. */
    private const val GLOW = 30
    private const val RAIN_GHOST = 16
    /** Clouds behind rain, snow and lightning, so what falls out of them stands out. */
    private const val STORM_CLOUD = 0.55f
    private const val DESIGN = 13

    /** The standard weather-icon set, one per group of WMO weather codes. */
    enum class Condition(val label: String) {
        CLEAR_DAY("Clear"),
        CLEAR_NIGHT("Clear"),
        PARTLY_CLOUDY_DAY("Partly cloudy"),
        PARTLY_CLOUDY_NIGHT("Partly cloudy"),
        CLOUDY("Cloudy"),
        FOG("Fog"),
        DRIZZLE("Drizzle"),
        RAIN("Rain"),
        HEAVY_RAIN("Heavy rain"),
        SLEET("Sleet"),
        SNOW("Snow"),
        THUNDERSTORM("Thunderstorm"),
    }

    /** Maps an Open-Meteo / WMO weather code to an icon, or null for a code we don't know. */
    fun condition(code: Int, isDay: Boolean): Condition? = when (code) {
        0, 1 -> if (isDay) Condition.CLEAR_DAY else Condition.CLEAR_NIGHT
        2 -> if (isDay) Condition.PARTLY_CLOUDY_DAY else Condition.PARTLY_CLOUDY_NIGHT
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        51, 53, 55 -> Condition.DRIZZLE
        61, 63, 80, 81 -> Condition.RAIN
        65, 82 -> Condition.HEAVY_RAIN
        56, 57, 66, 67 -> Condition.SLEET // freezing drizzle and freezing rain
        71, 73, 75, 77, 85, 86 -> Condition.SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> null
    }

    // Sprite brightness: # 255, * 190, + 130, : 70, , 30 (glow), . off. Lit from above (and the moon from the left).
    private val sun = listOf(
        ".*#*.",
        "*###*",
        "#####",
        "*###*",
        ".*#*.",
    )
    private val moon = listOf(
        "..*+,..",
        ".#*+,,.",
        "##*+,,,",
        "#+*+,,,",
        "##*+,,,",
        ".#:+,,.",
        "..*+,..",
    )
    private val smallMoon = listOf(
        ".*+,.",
        "#*+,,",
        "#:+,,",
        "#*+,,",
        ".*+,.",
    )
    private val cloud = listOf(
        "......##...",
        "..##.#**#..",
        ".#*******#.",
        "*+++++++++*",
        ".:::::::::.",
    )
    private val smallCloud = listOf(
        "....##..",
        ".##.#*#.",
        "#******#",
        ".::::::.",
    )
    private val bolt = listOf(
        "..#.",
        ".#..",
        "####",
        "..#.",
        ".#..",
        "#...",
    )

    private enum class Intro { RADIAL, FROM_LEFT, FROM_TOP, STRIKE }

    private fun introOf(condition: Condition) = when (condition) {
        Condition.CLEAR_DAY, Condition.CLEAR_NIGHT, Condition.PARTLY_CLOUDY_DAY, Condition.PARTLY_CLOUDY_NIGHT -> Intro.RADIAL
        Condition.CLOUDY, Condition.FOG -> Intro.FROM_LEFT
        Condition.DRIZZLE, Condition.RAIN, Condition.HEAVY_RAIN, Condition.SLEET, Condition.SNOW -> Intro.FROM_TOP
        Condition.THUNDERSTORM -> Intro.STRIKE
    }

    /**
     * Frame [f] of [condition] on a [size]×[size] grid: row-major brightness, 0 = off.
     * Frames 0 until [INTRO_FRAMES] are the intro; from there it loops every [LOOP_FRAMES], so any f ≥ 0 works.
     */
    fun frame(condition: Condition, f: Int, size: Int): IntArray =
        if (f >= INTRO_FRAMES) draw(condition, (f - INTRO_FRAMES) % LOOP_FRAMES, size)
        else intro(draw(condition, 0, size), f, introOf(condition), size)

    /** The glow fades up first, then the icon sweeps in; the last intro frame is the loop's first. */
    private fun intro(base: IntArray, f: Int, kind: Intro, size: Int): IntArray {
        val out = IntArray(base.size)
        val ghost = min(1f, (f + 1) / 3f)
        val progress = (f - 1) / (INTRO_FRAMES - 2f)
        val c = (size - 1) / 2f
        for (i in base.indices) {
            val v = base[i]
            if (v == 0) continue
            val x = i % size - c
            val y = i / size - c
            out[i] = when (kind) {
                Intro.STRIKE -> when {
                    f < 3 -> min(v, GLOW) * ghost
                    f < 5 -> FULL.toFloat() // the first strike lights the whole storm
                    else -> v + (FULL - v) * (INTRO_FRAMES - 1 - f) / 3f
                }
                else -> {
                    val s = when (kind) {
                        Intro.RADIAL -> hypot(x, y) / 6.5f
                        Intro.FROM_LEFT -> (x + 6.5f) / 13f
                        else -> (y + 6.5f) / 13f
                    }.coerceIn(0f, 1f)
                    val reveal = ((progress * 1.3f - s) / 0.3f).coerceIn(0f, 1f)
                    max(min(v, GLOW) * ghost, v * reveal)
                }
            }.roundToInt()
        }
        return out
    }

    /** Loop frame [t] (0 until [LOOP_FRAMES]). */
    private fun draw(condition: Condition, t: Int, size: Int): IntArray {
        val c = Canvas(size)
        when (condition) {
            Condition.CLEAR_DAY -> {
                c.sprite(sun, 4, 4)
                rays(c, t)
            }
            Condition.CLEAR_NIGHT -> {
                c.sprite(moon, 2, 3, shimmer = t)
                stars(c, t, listOf(10 to 2, 11 to 6, 10 to 10))
            }
            Condition.PARTLY_CLOUDY_DAY -> {
                c.sprite(sun, 2, 2)
                for ((i, r) in listOf(4 to 0, 0 to 4, 7 to 1, 1 to 7).withIndex()) {
                    c.set(r.first, r.second, GLOW + (FULL - GLOW) * wave(t + i * 8, 16))
                }
                c.sprite(cloud, 1 + drift(t), 6, knockout = true, shimmer = t)
            }
            Condition.PARTLY_CLOUDY_NIGHT -> {
                c.sprite(smallMoon, 1, 1)
                stars(c, t, listOf(9 to 2))
                c.sprite(cloud, 1 + drift(t), 6, knockout = true, shimmer = t)
            }
            Condition.CLOUDY -> {
                c.sprite(smallCloud, 4 - drift(t), 2, scale = 0.6f, shimmer = t + 16)
                c.sprite(cloud, drift(t), 5, knockout = true, shimmer = t)
            }
            Condition.FOG -> {
                // Wisps of brightness drift along each bank, alternating direction.
                for ((i, band) in listOf(3 to 1f, 5 to 0.6f, 7 to 1f, 9 to 0.6f).withIndex()) {
                    val dir = if (i % 2 == 0) 1 else -1
                    for (x in 0 until DESIGN) {
                        val wisp = 0.15f + 0.85f * wave(x * 4 - dir * t * 2 + i * 7, LOOP_FRAMES)
                        c.set(x, band.first, max(GLOW.toFloat(), FULL * band.second * wisp))
                    }
                }
            }
            Condition.DRIZZLE -> {
                stormCloud(c, t)
                rain(c, t, xs = intArrayOf(3, 6, 9), offsets = intArrayOf(0, 10, 5), period = 16, trail = intArrayOf(FULL, 70))
            }
            Condition.RAIN -> {
                stormCloud(c, t)
                rain(c, t, xs = intArrayOf(3, 6, 9), offsets = intArrayOf(0, 5, 2), period = 8, trail = intArrayOf(FULL, 130, 50))
            }
            Condition.HEAVY_RAIN -> {
                stormCloud(c, t)
                rain(
                    c, t, xs = intArrayOf(2, 4, 6, 8, 10), offsets = intArrayOf(0, 3, 1, 2, 0),
                    period = 4, trail = intArrayOf(FULL, 170, 90, 40),
                )
            }
            Condition.SLEET -> {
                stormCloud(c, t)
                rain(c, t, xs = intArrayOf(3, 9), offsets = intArrayOf(0, 4), period = 8, trail = intArrayOf(FULL, 130, 50))
                snow(c, t, xs = intArrayOf(5, 7), offsets = intArrayOf(4, 20))
            }
            Condition.SNOW -> {
                stormCloud(c, t)
                snow(c, t, xs = intArrayOf(2, 5, 7, 9), offsets = intArrayOf(0, 12, 22, 6))
            }
            Condition.THUNDERSTORM -> {
                // Double flash, then the light dies away over a few frames.
                val flash = when (t) {
                    4 -> 1f; 5 -> 0.5f; 6 -> 1f; 7 -> 0.7f; 8 -> 0.45f; 9 -> 0.25f; 10 -> 0.1f
                    else -> 0f
                }
                c.sprite(cloud, 1, 1, scale = STORM_CLOUD + (1 - STORM_CLOUD) * flash)
                // The bolt lights the cloud's underside from below.
                for (x in 2..10) c.set(x, 5, FULL * 0.8f * flash)
                c.sprite(bolt, 5, 6, scale = (GLOW + (FULL - GLOW) * flash) / FULL)
                rain(c, t, xs = intArrayOf(2, 10), offsets = intArrayOf(0, 4), period = 8, trail = intArrayOf(FULL, 130, 50))
            }
        }
        return c.px
    }

    /**
     * Eight rays, all faintly visible, with two opposite beams sweeping round once per loop.
     * A ray grows its outer LED as it brightens.
     */
    private fun rays(c: Canvas, t: Int) {
        val dirs = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)
        for ((k, d) in dirs.withIndex()) {
            val inner = if (d.first != 0 && d.second != 0) 3 else 4
            val b = wave(t - k * 4, 16)
            c.set(6 + d.first * inner, 6 + d.second * inner, GLOW + (FULL - GLOW) * (0.35f + 0.65f * b))
            c.set(6 + d.first * (inner + 1), 6 + d.second * (inner + 1), GLOW + (FULL - GLOW) * b * b)
        }
    }

    /** Stars twinkle in turn; at their brightest a faint cross of light shows round them. */
    private fun stars(c: Canvas, t: Int, at: List<Pair<Int, Int>>) {
        for ((i, s) in at.withIndex()) {
            val b = wave(t + i * 11, LOOP_FRAMES)
            c.set(s.first, s.second, GLOW + (FULL - GLOW) * b)
            val halo = 50 * ((b - 0.7f) / 0.3f).coerceAtLeast(0f)
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) c.set(s.first + dx, s.second + dy, halo)
        }
    }

    /** Clouds drift one LED over and back once per loop, cross-fading rather than jumping. */
    private fun drift(t: Int): Float {
        fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        return ease((t - 6) / 6f) - ease((t - 20) / 6f)
    }

    private fun stormCloud(c: Canvas, t: Int) = c.sprite(cloud, 1, 1, scale = STORM_CLOUD, shimmer = t)

    /**
     * Drops falling from the cloud with fading trails, each column faintly showing its path.
     * A drop takes [period] frames (dividing [LOOP_FRAMES]) to fall from row 7 past the bottom, and splashes when it lands.
     */
    private fun rain(c: Canvas, t: Int, xs: IntArray, offsets: IntArray, period: Int, trail: IntArray) {
        val path = 8 // rows 7‥14; past the bottom of the matrix, so drops leave cleanly
        val speed = path / period.toFloat()
        for (i in xs.indices) {
            val x = xs[i]
            val bottom = (12 downTo 7).first { DigitFont.hasLed(x, it, DESIGN) }
            for (y in 7..bottom) c.set(x, y, RAIN_GHOST.toFloat())
            val head = 7 + ((t + offsets[i]) % period) * path / period
            for ((k, v) in trail.withIndex()) if (head - k in 7..bottom) c.set(x, head - k, v.toFloat())
            if (head > bottom && head - speed <= bottom) {
                c.set(x - 1, bottom, 70f)
                c.set(x + 1, bottom, 70f)
            }
        }
    }

    /** Flakes drifting down a quarter LED a frame, swaying and twinkling as they go. */
    private fun snow(c: Canvas, t: Int, xs: IntArray, offsets: IntArray) {
        for (i in xs.indices) {
            val p = (t + offsets[i]) % LOOP_FRAMES
            val y = 7 + p / 4
            if (y > 12) continue
            val v = FULL * (0.7f + 0.3f * wave(p * 2 + i * 5, 16))
            c.setBetween(xs[i] + wave(p + i * 3, 16), y, v)
        }
    }

    /** 0 → 1 → 0 smoothly over [period] frames. */
    private fun wave(t: Int, period: Int): Float = (0.5 - 0.5 * cos(2 * PI * t / period)).toFloat()

    /** Draws in 13×13 design coordinates, centred on the real grid; anything off the round matrix is dropped. */
    private class Canvas(val size: Int) {
        val px = IntArray(size * size)
        private val offset = (size - DESIGN) / 2

        private fun index(x: Int, y: Int): Int? {
            val sx = x + offset
            val sy = y + offset
            return if (sx in 0 until size && sy in 0 until size && DigitFont.hasLed(sx, sy, size)) sy * size + sx else null
        }

        /** Lights ([x], [y]) at [v], keeping whatever is already brighter there. */
        fun set(x: Int, y: Int, v: Float) {
            val i = index(x, y) ?: return
            px[i] = max(px[i], v.roundToInt().coerceIn(0, FULL))
        }

        /** Splits [v] between the two LEDs either side of a fractional [x]. */
        fun setBetween(x: Float, y: Int, v: Float) {
            val x0 = floor(x).toInt()
            val w = x - x0
            set(x0, y, v * (1 - w))
            set(x0 + 1, y, v * w)
        }

        private fun clear(x: Int, y: Int) {
            index(x, y)?.let { px[it] = 0 }
        }

        /**
         * Draws shaded [rows] at ([x], [y]); a fractional x cross-fades between the two LEDs it falls between.
         * With [knockout], first clears a 1-LED border so it stands in front of what's behind.
         * With [shimmer] (the loop frame), a soft highlight travels along the brightest pixels.
         */
        fun sprite(rows: List<String>, x: Number, y: Int, scale: Float = 1f, knockout: Boolean = false, shimmer: Int? = null) {
            val fx = x.toFloat()
            val x0 = floor(fx).toInt()
            val w = fx - x0
            val placements = listOf(x0 to 1 - w, x0 + 1 to w).filter { it.second > 0.01f }
            if (knockout) for ((px0, _) in placements) forEachLit(rows) { cx, cy, _ ->
                for (dy in -1..1) for (dx in -1..1) clear(px0 + cx + dx, y + cy + dy)
            }
            val acc = HashMap<Pair<Int, Int>, Float>()
            for ((px0, weight) in placements) forEachLit(rows) { cx, cy, level ->
                val gx = px0 + cx
                var v = level * scale
                if (shimmer != null && level == FULL) v *= 0.78f + 0.22f * wave(shimmer - gx * 3, LOOP_FRAMES)
                acc.merge(gx to y + cy, v * weight, Float::plus)
            }
            for ((p, v) in acc) set(p.first, p.second, v)
        }

        private inline fun forEachLit(rows: List<String>, action: (Int, Int, Int) -> Unit) {
            for (r in rows.indices) for (col in rows[r].indices) {
                val level = when (rows[r][col]) {
                    '#' -> FULL; '*' -> 190; '+' -> 130; ':' -> 70; ',' -> GLOW
                    else -> 0
                }
                if (level > 0) action(col, r, level)
            }
        }
    }
}
