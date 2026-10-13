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
        )
    )

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

    /** Draws [text] centred, or returns null if it doesn't fit in the square at all. */
    private fun draw(text: String, font: Font, size: Int): BooleanArray? {
        val w = width(text, font)
        if (w > size || font.height > size) return null
        val out = BooleanArray(size * size)
        var x = (size - w) / 2
        val top = (size - font.height) / 2
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
