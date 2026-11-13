package com.watstack.glyphweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightIconsTest {
    private val size = 13 // Phone (4a) Pro matrix

    private fun ascii(pixels: IntArray) = (0 until size).joinToString("\n") { y ->
        (0 until size).joinToString("") { x ->
            val v = pixels[y * size + x]
            when {
                v >= 200 -> "#"
                v > 0 -> "+"
                DigitFont.hasLed(x, y, size) -> "."
                else -> " "
            }
        }
    }

    @Test
    fun snapsTrackToCompassDirections() {
        assertEquals(0, FlightIcons.octant(0.0))
        assertEquals(0, FlightIcons.octant(359.0))
        assertEquals(1, FlightIcons.octant(47.3))
        assertEquals(2, FlightIcons.octant(90.0))
        assertEquals(4, FlightIcons.octant(181.0))
        assertEquals(6, FlightIcons.octant(-90.0))
        assertEquals(7, FlightIcons.octant(320.0))
        assertEquals(2, FlightIcons.octant(null))
    }

    @Test
    fun spritesPointTheRightWay() {
        // The nose is the lone lit cell at the leading edge.
        assertEquals("...#...", FlightIcons.sprite(0).first())
        assertEquals("...#...", FlightIcons.sprite(4).last())
        assertEquals('#', FlightIcons.sprite(2)[3].last())
        assertEquals('#', FlightIcons.sprite(6)[3].first())
        assertEquals('#', FlightIcons.sprite(1)[0].last()) // NE: top right
        assertEquals('#', FlightIcons.sprite(3)[6].last()) // SE: bottom right
        assertEquals('#', FlightIcons.sprite(5)[6].first()) // SW: bottom left
        assertEquals('#', FlightIcons.sprite(7)[0].first()) // NW: top left
    }

    @Test
    fun crossingStartsAndEndsOffTheMatrixAndStaysOnLeds() {
        for (track in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
            val frames = (0 until FlightIcons.FRAMES).map { FlightIcons.frame(track, it, size) }
            assertTrue("$track starts with the plane off the matrix", frames.first().none { it >= 200 })
            assertTrue("$track ends with the plane off the matrix", frames.last().none { it >= 200 })
            assertTrue("$track passes through the middle", frames[FlightIcons.FRAMES / 2].any { it >= 200 })
            for ((f, px) in frames.withIndex()) {
                for (i in px.indices) if (px[i] > 0) assertTrue("$track frame $f pixel $i off-matrix", DigitFont.hasLed(i % size, i / size, size))
            }
            println("track $track, middle frame:\n${ascii(frames[FlightIcons.FRAMES / 2])}\n")
        }
    }
}
