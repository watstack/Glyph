package com.watstack.glyphweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitFontTest {
    private val size = 13 // Phone (4a) Pro matrix

    private fun ascii(pixels: BooleanArray) = (0 until size).joinToString("\n") { y ->
        (0 until size).joinToString("") { x ->
            when {
                pixels[y * size + x] -> "#"
                DigitFont.hasLed(x, y, size) -> "."
                else -> " "
            }
        }
    }

    private fun litRows(px: BooleanArray) = (0 until size).filter { r -> (0 until size).any { c -> px[r * size + c] } }
    private fun litCols(px: BooleanArray) = (0 until size).filter { c -> (0 until size).any { r -> px[r * size + c] } }

    @Test
    fun ledMaskMatchesPhone4aPro() {
        val count = (0 until size * size).count { DigitFont.hasLed(it % size, it / size, size) }
        assertEquals(137, count)
    }

    @Test
    fun labelsRound() {
        assertEquals("18", DigitFont.label(17.6))
        assertEquals("-3", DigitFont.label(-2.6))
        assertEquals("--", DigitFont.label(null))
    }

    @Test
    fun everyValueIsOnRealLedsAndCentred() {
        for (t in listOf("0", "7", "18", "-5", "-12", "103", "--")) {
            val px = DigitFont.render(t, size)
            println("$t:\n${ascii(px)}\n")
            assertTrue("$t lights something", px.any { it })
            for (i in px.indices) if (px[i]) assertTrue("$t pixel $i off-matrix", DigitFont.hasLed(i % size, i / size, size))
            val cols = litCols(px)
            val rows = litRows(px)
            assertTrue("$t horizontally centred", kotlin.math.abs(cols.first() - (size - 1 - cols.last())) <= 1)
            assertTrue("$t vertically centred", kotlin.math.abs(rows.first() - (size - 1 - rows.last())) <= 1)
        }
    }

    @Test
    fun twoDigitsUseLargeFont() {
        assertEquals(7, litRows(DigitFont.render("18", size)).size)
        val cols = litCols(DigitFont.render("-5", size))
        assertEquals(11, cols.last() - cols.first() + 1)
    }

    @Test
    fun uvScreenFitsTheRoundMatrix() {
        val uvRows = (1..5).flatMap { r -> (0 until size).map { c -> r * size + c } }
        val reference = DigitFont.renderUv(0.0, size)
        for (uv in (0..15).map { it.toDouble() } + listOf<Double?>(null, 7.4)) {
            val px = DigitFont.renderUv(uv, size)
            println("UV $uv:\n${ascii(px)}\n")
            for (i in px.indices) if (px[i]) assertTrue("UV $uv pixel $i off-matrix", DigitFont.hasLed(i % size, i / size, size))
            val numberRows = if (uv == null) listOf(9) else (7..11).toList() // "--" is a single dash mid-way
            assertEquals("UV $uv uses rows 1-5 and its number rows", (1..5).toList() + numberRows, litRows(px))
            assertTrue("UV $uv keeps the same UV letters", uvRows.all { px[it] == reference[it] })
            val cols = litCols(px)
            assertTrue("UV $uv horizontally centred", kotlin.math.abs(cols.first() - (size - 1 - cols.last())) <= 1)
        }
        assertTrue("different values draw differently", !DigitFont.renderUv(3.0, size).contentEquals(DigitFont.renderUv(8.0, size)))
        assertTrue("7.4 rounds to 7", DigitFont.renderUv(7.4, size).contentEquals(DigitFont.renderUv(7.0, size)))
    }

    @Test
    fun scrollingTickerStaysOnTheMiddleRows() {
        val text = "QF401 MEL>SYD B738"
        val steps = DigitFont.scrollSteps(text, size)
        assertEquals(16 * 3 + 2 * 2 + 17 + size, steps) // 16 3-wide glyphs, 2 2-wide spaces, 17 gaps
        assertTrue("starts blank", DigitFont.renderScroll(text, 0, size).none { it })
        assertTrue("ends blank", DigitFont.renderScroll(text, steps, size).none { it })
        for (s in 0 until steps) {
            val px = DigitFont.renderScroll(text, s, size)
            for (r in litRows(px)) assertTrue("step $s row $r", r in 4..8)
        }
        println(ascii(DigitFont.renderScroll(text, size, size)))
    }

    @Test
    fun everyTickerCharacterDraws() {
        for (ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789->/") {
            assertTrue("$ch draws", DigitFont.renderScroll("$ch", size - 2, size).any { it })
        }
        assertTrue("lowercase is upper-cased", DigitFont.renderScroll("qf", 6, size).contentEquals(DigitFont.renderScroll("QF", 6, size)))
        assertTrue("O and 0 differ", !DigitFont.renderScroll("O", 8, size).contentEquals(DigitFont.renderScroll("0", 8, size)))
    }

    @Test
    fun altitudeScreen() {
        assertEquals("35K", DigitFont.altitudeLabel(35_000))
        assertEquals("12K", DigitFont.altitudeLabel(12_025))
        assertEquals("2K", DigitFont.altitudeLabel(1_500))
        assertEquals("800", DigitFont.altitudeLabel(875))
        assertEquals("--", DigitFont.altitudeLabel(null))
        for (ft in listOf(null, 500, 3_000, 35_000, 45_000)) {
            val px = DigitFont.renderAltitude(ft, size)
            println("$ft ft:\n${ascii(px)}\n")
            assertTrue("$ft lights something", px.any { it })
            for (i in px.indices) if (px[i]) assertTrue("$ft pixel $i off-matrix", DigitFont.hasLed(i % size, i / size, size))
        }
    }
}
