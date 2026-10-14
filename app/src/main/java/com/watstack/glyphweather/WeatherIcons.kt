package com.watstack.glyphweather

/**
 * Animated weather icons for the Glyph Matrix. Drawn for the 13×13 Phone (4a) Pro and centred on larger matrices.
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 */
object WeatherIcons {
    /** One animation is [FRAMES] × [FRAME_MS] = 3 seconds, and loops seamlessly. */
    const val FRAMES = 12
    const val FRAME_MS = 250L

    const val FULL = 255
    private const val MID = 150
    /** Clouds behind rain, snow and lightning, so what falls out of them stands out. */
    private const val DIM = 90
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

    private val sun = listOf(
        ".###.",
        "#####",
        "#####",
        "#####",
        ".###.",
    )
    private val moon = listOf(
        "..####",
        ".###..",
        "###...",
        "###...",
        "###...",
        ".###..",
        "..####",
    )
    private val smallMoon = listOf(
        "..###",
        ".##..",
        "##...",
        "##...",
        ".##..",
        "..###",
    )
    private val cloud = listOf(
        "......##...",
        "..##.####..",
        ".#########.",
        "###########",
        ".#########.",
    )
    private val smallCloud = listOf(
        "....##..",
        ".##.###.",
        "########",
        ".######.",
    )
    private val bolt = listOf(
        "..#.",
        ".#..",
        "####",
        "..#.",
        ".#..",
        "#...",
    )

    /** Frame [f] (0 until [FRAMES]) of [condition] on a [size]×[size] grid: row-major brightness, 0 = off. */
    fun frame(condition: Condition, f: Int, size: Int): IntArray {
        val c = Canvas(size)
        when (condition) {
            Condition.CLEAR_DAY -> clearDay(c, f)
            Condition.CLEAR_NIGHT -> clearNight(c, f)
            Condition.PARTLY_CLOUDY_DAY -> {
                c.sprite(sun, 2, 2, FULL)
                // Short rays that twinkle in pairs.
                val a = (f / 3) % 2 == 0
                c.set(4, 0, if (a) FULL else MID)
                c.set(0, 4, if (a) MID else FULL)
                c.set(7, 1, if (a) MID else FULL)
                c.set(1, 7, if (a) FULL else MID)
                c.sprite(cloud, 1 + drift(f), 6, FULL, knockout = true)
            }
            Condition.PARTLY_CLOUDY_NIGHT -> {
                c.sprite(smallMoon, 1, 1, FULL)
                c.set(9, 2, if ((f / 3) % 2 == 0) FULL else DIM)
                c.sprite(cloud, 1 + drift(f), 6, FULL, knockout = true)
            }
            Condition.CLOUDY -> {
                c.sprite(smallCloud, 4 - drift(f), 2, MID)
                c.sprite(cloud, drift(f), 5, FULL, knockout = true)
            }
            Condition.FOG -> {
                // Banks of fog sliding past each other.
                val s = (f / 3) % 2
                c.hline(2 + s, 9 + s, 3, FULL)
                c.hline(3 - s, 11 - s, 5, MID)
                c.hline(1 + s, 9 + s, 7, FULL)
                c.hline(3 - s, 10 - s, 9, MID)
            }
            Condition.DRIZZLE -> {
                stormCloud(c)
                fall(c, f, xs = intArrayOf(3, 6, 9), offsets = intArrayOf(0, 4, 2), speed = 1, length = 1)
            }
            Condition.RAIN -> {
                stormCloud(c)
                fall(c, f, xs = intArrayOf(3, 6, 9), offsets = intArrayOf(0, 4, 2), speed = 2, length = 2)
            }
            Condition.HEAVY_RAIN -> {
                stormCloud(c)
                fall(c, f, xs = intArrayOf(2, 4, 6, 8, 10), offsets = intArrayOf(0, 3, 1, 4, 2), speed = 2, length = 3)
            }
            Condition.SLEET -> {
                stormCloud(c)
                fall(c, f, xs = intArrayOf(3, 9), offsets = intArrayOf(0, 3), speed = 2, length = 2)
                snow(c, f, xs = intArrayOf(6), offsets = intArrayOf(2))
            }
            Condition.SNOW -> {
                stormCloud(c)
                snow(c, f, xs = intArrayOf(3, 6, 9), offsets = intArrayOf(0, 3, 1))
            }
            Condition.THUNDERSTORM -> {
                // Double flash: on, off, on again for longer, then dark with a little rain.
                val flash = f in 1..2 || f in 4..7
                c.sprite(cloud, 1, 1, if (flash) MID else DIM)
                if (flash) c.sprite(bolt, 5, 6, FULL)
                fall(c, f, xs = intArrayOf(2, 10), offsets = intArrayOf(0, 3), speed = 2, length = 2)
            }
        }
        return c.px
    }

    private fun clearDay(c: Canvas, f: Int) {
        c.sprite(sun, 4, 4, FULL)
        // Straight and diagonal rays take turns being long, which reads as the sun turning.
        val straightLong = (f / 3) % 2 == 0
        for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) ray(c, dx, dy, inner = 4, long = straightLong)
        for ((dx, dy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) ray(c, dx, dy, inner = 3, long = !straightLong)
    }

    /** A ray from the centre (6, 6) in direction ([dx], [dy]), starting [inner] cells out; 2 cells when [long]. */
    private fun ray(c: Canvas, dx: Int, dy: Int, inner: Int, long: Boolean) {
        c.set(6 + dx * inner, 6 + dy * inner, FULL)
        if (long) c.set(6 + dx * (inner + 1), 6 + dy * (inner + 1), FULL)
    }

    private fun clearNight(c: Canvas, f: Int) {
        c.sprite(moon, 2, 3, FULL)
        // Stars twinkle one after another.
        val stars = listOf(10 to 2, 9 to 6, 11 to 9)
        for ((i, s) in stars.withIndex()) c.set(s.first, s.second, if ((f / 2 + i * 2) % 6 < 4) FULL else DIM)
    }

    /** Clouds drift one LED over and back once per animation. */
    private fun drift(f: Int) = if (f in 3..8) 1 else 0

    private fun stormCloud(c: Canvas) = c.sprite(cloud, 1, 1, DIM)

    /** Drops falling from the cloud (rows 7–12), looping seamlessly over [FRAMES]. */
    private fun fall(c: Canvas, f: Int, xs: IntArray, offsets: IntArray, speed: Int, length: Int) {
        for (i in xs.indices) {
            val head = 7 + (f * speed + offsets[i]) % 6
            for (k in 0 until length) if (head - k >= 7) c.set(xs[i], head - k, FULL)
        }
    }

    /** Flakes falling slowly and swaying from side to side. */
    private fun snow(c: Canvas, f: Int, xs: IntArray, offsets: IntArray) {
        for (i in xs.indices) {
            val step = f / 2 + offsets[i]
            c.set(xs[i] + if (step % 2 == 0) 0 else 1, 7 + step % 6, FULL)
        }
    }

    /** Draws in 13×13 design coordinates, centred on the real grid; anything off the round matrix is dropped. */
    private class Canvas(val size: Int) {
        val px = IntArray(size * size)
        private val offset = (size - DESIGN) / 2

        fun set(x: Int, y: Int, v: Int) {
            val sx = x + offset
            val sy = y + offset
            if (sx in 0 until size && sy in 0 until size && DigitFont.hasLed(sx, sy, size)) px[sy * size + sx] = v
        }

        fun hline(x0: Int, x1: Int, y: Int, v: Int) {
            for (x in x0..x1) set(x, y, v)
        }

        /** Draws [rows] at ([x], [y]). With [knockout], first clears a 1-LED border so it stands in front of what's behind. */
        fun sprite(rows: List<String>, x: Int, y: Int, v: Int, knockout: Boolean = false) {
            if (knockout) forEachLit(rows) { cx, cy ->
                for (dy in -1..1) for (dx in -1..1) set(x + cx + dx, y + cy + dy, 0)
            }
            forEachLit(rows) { cx, cy -> set(x + cx, y + cy, v) }
        }

        private inline fun forEachLit(rows: List<String>, action: (Int, Int) -> Unit) {
            for (r in rows.indices) for (col in rows[r].indices) if (rows[r][col] == '#') action(col, r)
        }
    }
}
