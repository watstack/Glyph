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
}
