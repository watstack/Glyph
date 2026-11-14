package com.watstack.glyphweather

/**
 * Pixel fonts for drawing a temperature on the round Glyph Matrix.
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 */
object DigitFont {
    private const val GAP = 1

    private class Font(val height: Int, val glyphs: Map<Char, List<String>>)

    /** 5×7: used for one or two characters, e.g. "18" or "7°". */
    private val large = Font(
        7, mapOf(
            '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
            '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to listOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
            '-' to listOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
            '°' to listOf("##", "##", "..", "..", "..", "..", ".."),
        )
    )

    /** 3×5: fallback for three characters, e.g. "-12" or "103". */
    private val small = Font(
        5, mapOf(
            '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
            '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
            '2' to listOf("###", "..#", "###", "#..", "###"),
            '3' to listOf("###", "..#", ".##", "..#", "###"),
            '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
            '5' to listOf("###", "#..", "###", "..#", "###"),
            '6' to listOf("###", "#..", "###", "#.#", "###"),
            '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
            '8' to listOf("###", "#.#", "###", "#.#", "###"),
            '9' to listOf("###", "#.#", "###", "..#", "###"),
            '-' to listOf("...", "...", "###", "...", "..."),
            '°' to listOf("##", "##", "..", "..", ".."),
            // Letters and symbols for the scrolling flight ticker ("QF401  -  MEL>SYD  -  B738  -  830 KM/H").
            'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
            'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
            'C' to listOf(".##", "#..", "#..", "#..", ".##"),
            'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
            'E' to listOf("###", "#..", "##.", "#..", "###"),
            'F' to listOf("###", "#..", "##.", "#..", "#.."),
            'G' to listOf(".##", "#..", "#.#", "#.#", ".##"),
            'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
            'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
            'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
            'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
            'L' to listOf("#..", "#..", "#..", "#..", "###"),
            'M' to listOf("#.#", "###", "###", "#.#", "#.#"),
            'N' to listOf("###", "#.#", "#.#", "#.#", "#.#"),
            'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."), // rounder than 0, so the two differ
            'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
            'Q' to listOf(".#.", "#.#", "#.#", "##.", ".##"),
            'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
            'S' to listOf(".##", "#..", ".#.", "..#", "##."),
            'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
            'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
            'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
            'W' to listOf("#.#", "#.#", "###", "###", "#.#"),
            'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
            'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
            'Z' to listOf("###", "..#", ".#.", "#..", "###"),
            '>' to listOf("#..", ".#.", "..#", ".#.", "#.."),
            '/' to listOf("..#", "..#", ".#.", "#..", "#.."),
            ' ' to listOf("..", "..", "..", "..", ".."),
        )
    )

    /** Columns one scroll of [text] takes: in from the right edge until its last column has left on the left. */
    fun scrollSteps(text: String, size: Int): Int = width(clean(text), small) + size

    /**
     * Step [step] (0 until [scrollSteps]) of [text] scrolling right to left through the middle rows in the 3×5 font.
     * On the 13×13 matrix those are rows 4–8, which have LEDs all the way across.
     */
    fun renderScroll(text: String, step: Int, size: Int): BooleanArray {
        val out = BooleanArray(size * size)
        val top = (size - small.height) / 2
        var x = size - step
        for (ch in clean(text)) {
            val g = small.glyphs[ch] ?: continue
            for (row in g.indices) for (col in g[row].indices) {
                val sx = x + col
                if (g[row][col] == '#' && sx in 0 until size && hasLed(sx, top + row, size)) out[(top + row) * size + sx] = true
            }
            x += g[0].length + GAP
        }
        return out
    }

    /** Altitude for the matrix: thousands of feet with K ("35K" for 35,000 ft), hundreds below 1,000 ft. */
    fun altitudeLabel(feet: Int?): String = when {
        feet == null -> "--"
        feet >= 1000 -> "${(feet + 500) / 1000}K"
        else -> "${(feet.coerceAtLeast(0) / 100) * 100}"
    }

    /** The altitude screen: [altitudeLabel] centred in the 3×5 font. */
    fun renderAltitude(feet: Int?, size: Int): BooleanArray =
        draw(altitudeLabel(feet), small, size) ?: BooleanArray(size * size)

    /** Upper-cases [text] and drops characters the 3×5 font can't draw. */
    private fun clean(text: String): String = text.uppercase().filter { it in small.glyphs }

    /**
     * The UV screen: small "UV" on top and the rounded index underneath ("--" when unknown), centred as one block
     * with a blank row between. On the 13×13 matrix that's rows 1–5 and 7–11, which fits values up to 99.
     */
    fun renderUv(uv: Double?, size: Int): BooleanArray {
        val out = BooleanArray(size * size)
        val block = small.height * 2 + 1
        if (block > size) return out
        val top = (size - block) / 2
        val label = if (uv == null || uv.isNaN()) "--" else Math.round(uv.coerceAtLeast(0.0)).toString()
        for ((text, y) in listOf("UV" to top, label to top + small.height + 1)) {
            val line = draw(text, small, size, y) ?: continue
            for (i in line.indices) if (line[i]) out[i] = true
        }
        return out
    }

    /** Text to show for a temperature, or "--" when unknown. */
    fun label(temperature: Double?): String =
        if (temperature == null || temperature.isNaN()) "--" else Math.round(temperature).toString()

    /**
     * Whether an LED exists at ([x], [y]). The matrix is round: on the 13×13 Phone (4a) Pro
     * the 137 LEDs are exactly the cells within radius 6.5 of the centre.
     */
    fun hasLed(x: Int, y: Int, size: Int): Boolean {
        val c = (size - 1) / 2.0
        val r = size / 2.0
        return (x - c) * (x - c) + (y - c) * (y - c) <= r * r
    }

    /**
     * Renders [text] centred on a [size]×[size] grid. Returns row-major pixels, true = lit.
     * Picks the first layout whose lit pixels all land on real LEDs:
     * large with "°", large without, small with "°", small without.
     */
    fun render(text: String, size: Int): BooleanArray {
        val withDegree = if (text == "--") text else "$text°"
        val candidates = listOf(withDegree to large, text to large, withDegree to small, text to small)
        for ((t, font) in candidates) {
            val px = draw(t, font, size) ?: continue
            if (px.indices.all { !px[it] || hasLed(it % size, it / size, size) }) return px
        }
        return draw(text, small, size) ?: BooleanArray(size * size)
    }

    /** Draws [text] centred (vertically too, unless [top] is given), or returns null if it doesn't fit in the square. */
    private fun draw(text: String, font: Font, size: Int, top: Int = (size - font.height) / 2): BooleanArray? {
        val w = width(text, font)
        if (w > size || top < 0 || top + font.height > size) return null
        val out = BooleanArray(size * size)
        var x = (size - w) / 2
        for (ch in text) {
            val g = font.glyphs[ch] ?: continue
            for (row in g.indices) for (col in g[row].indices) {
                if (g[row][col] == '#') out[(top + row) * size + x + col] = true
            }
            x += g[0].length + GAP
        }
        return out
    }

    private fun width(text: String, font: Font): Int =
        text.sumOf { font.glyphs[it]?.get(0)?.length ?: 0 } + (text.length - 1).coerceAtLeast(0) * GAP
}
