package com.watstack.glyphweather

import kotlin.math.roundToInt

/**
 * A plane flying across the Glyph Matrix in the direction it's really heading, trailing a dim contrail.
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 */
object FlightIcons {
    /** One crossing is [FRAMES] × [FRAME_MS] = 1.6 seconds; it plays [CROSSINGS] times. */
    const val FRAMES = 16
    const val FRAME_MS = 100L
    const val CROSSINGS = 2

    private const val TRAIL = 70
    /** How far the plane's centre starts and ends from the matrix centre: far enough to be fully off it. */
    private const val TRAVEL = 11

    /** Pointing north (up). Rotated for east, south and west. */
    private val north = listOf(
        "...#...",
        "...#...",
        "..###..",
        "#######",
        "...#...",
        "...#...",
        "..###..",
    )

    /** Pointing north-east. Rotated for south-east, south-west and north-west. */
    private val northEast = listOf(
        "..#...#",
        "...#.#.",
        "....#..",
        "...#.#.",
        "#.#...#",
        ".#.....",
        "..#....",
    )

    /** The 8 compass directions as (dx, dy) on screen, clockwise from north; y grows downwards. */
    private val directions = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)

    /** Which of the 8 compass directions (0 = N, 1 = NE, … 7 = NW) a track in degrees is closest to. */
    fun octant(trackDeg: Double?): Int {
        val t = trackDeg ?: 90.0 // no heading broadcast: fly it left to right
        return (((t % 360 + 360) % 360) / 45.0).roundToInt() % 8
    }

    /** The plane sprite for [octant], as rows of '#'. */
    fun sprite(octant: Int): List<String> {
        var s = if (octant % 2 == 0) north else northEast
        repeat(octant / 2) { s = rotate(s) }
        return s
    }

    /** Frame [f] (0 until [FRAMES]) of a plane on [trackDeg] crossing a [size]×[size] grid: row-major brightness. */
    fun frame(trackDeg: Double?, f: Int, size: Int): IntArray {
        val px = IntArray(size * size)
        val o = octant(trackDeg)
        val (dx, dy) = directions[o]
        val sprite = sprite(o)
        val half = sprite.size / 2
        // Centre of the plane travels from TRAVEL behind the middle to TRAVEL past it.
        val along = -TRAVEL + (2 * TRAVEL * f) / (FRAMES - 1)
        val cx = (size - 1) / 2 + dx * along
        val cy = (size - 1) / 2 + dy * along

        fun set(x: Int, y: Int, v: Int) {
            if (x in 0 until size && y in 0 until size && DigitFont.hasLed(x, y, size)) px[y * size + x] = v
        }
        // Contrail behind the tail.
        for (k in 4..7) set(cx - dx * k, cy - dy * k, TRAIL)
        for (r in sprite.indices) for (c in sprite[r].indices) {
            if (sprite[r][c] == '#') set(cx - half + c, cy - half + r, WeatherIcons.FULL)
        }
        return px
    }

    /** Rotates a square sprite a quarter turn clockwise. */
    private fun rotate(rows: List<String>): List<String> {
        val n = rows.size
        return (0 until n).map { y -> (0 until n).map { x -> rows[n - 1 - x][y] }.joinToString("") }
    }
}
