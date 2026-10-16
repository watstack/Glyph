package com.watstack.glyphweather

import com.watstack.glyphweather.WeatherIcons.Condition
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherIconsTest {
    private val size = 13 // Phone (4a) Pro matrix

    private fun ascii(pixels: IntArray, n: Int = size) = (0 until n).joinToString("\n") { y ->
        (0 until n).joinToString("") { x ->
            val v = pixels[y * n + x]
            when {
                v >= 200 -> "#"
                v > 0 -> "+"
                DigitFont.hasLed(x, y, n) -> "."
                else -> " "
            }
        }
    }

    @Test
    fun animationLastsFiveSeconds() {
        assertEquals(5_000L, WeatherIcons.ICON_FRAMES * WeatherIcons.FRAME_MS)
    }

    @Test
    fun mapsWmoCodes() {
        assertEquals(Condition.CLEAR_DAY, WeatherIcons.condition(0, isDay = true))
        assertEquals(Condition.CLEAR_NIGHT, WeatherIcons.condition(1, isDay = false))
        assertEquals(Condition.PARTLY_CLOUDY_DAY, WeatherIcons.condition(2, isDay = true))
        assertEquals(Condition.PARTLY_CLOUDY_NIGHT, WeatherIcons.condition(2, isDay = false))
        assertEquals(Condition.CLOUDY, WeatherIcons.condition(3, isDay = true))
        assertEquals(Condition.FOG, WeatherIcons.condition(45, isDay = true))
        assertEquals(Condition.DRIZZLE, WeatherIcons.condition(53, isDay = true))
        assertEquals(Condition.RAIN, WeatherIcons.condition(61, isDay = true))
        assertEquals(Condition.RAIN, WeatherIcons.condition(80, isDay = true))
        assertEquals(Condition.HEAVY_RAIN, WeatherIcons.condition(65, isDay = true))
        assertEquals(Condition.SLEET, WeatherIcons.condition(66, isDay = true))
        assertEquals(Condition.SNOW, WeatherIcons.condition(73, isDay = true))
        assertEquals(Condition.THUNDERSTORM, WeatherIcons.condition(95, isDay = false))
        assertNull(WeatherIcons.condition(42, isDay = true))
    }

    @Test
    fun everyIconAnimatesOnRealLeds() {
        for (c in Condition.values()) {
            val frames = (0 until WeatherIcons.ICON_FRAMES).map { WeatherIcons.frame(c, it, size) }
            println("$c:\n${ascii(frames[WeatherIcons.INTRO_FRAMES])}\n")
            for ((f, px) in frames.withIndex()) {
                assertTrue("$c frame $f lights something", px.any { it > 0 })
                for (i in px.indices) if (px[i] > 0) assertTrue("$c pixel $i off-matrix", DigitFont.hasLed(i % size, i / size, size))
            }
            val loop = frames.drop(WeatherIcons.INTRO_FRAMES)
            assertTrue("$c moves", loop.any { !it.contentEquals(loop[0]) })
        }
    }

    @Test
    fun iconsAreShadedWithAFaintGlow() {
        for (c in Condition.values()) {
            val levels = WeatherIcons.frame(c, WeatherIcons.INTRO_FRAMES, size).filter { it > 0 }.toSet()
            assertTrue("$c uses several brightness levels: $levels", levels.size >= 4)
            assertTrue("$c has a faint glow: $levels", levels.any { it <= 50 })
        }
    }

    @Test
    fun loopIsSeamlessAndIntroLeadsIntoIt() {
        val first = WeatherIcons.INTRO_FRAMES
        for (c in Condition.values()) {
            assertArrayEquals("$c loops", WeatherIcons.frame(c, first, size), WeatherIcons.frame(c, first + WeatherIcons.LOOP_FRAMES, size))
            assertArrayEquals("$c intro ends on the loop's first frame", WeatherIcons.frame(c, first, size), WeatherIcons.frame(c, first - 1, size))
            assertTrue("$c intro starts dim", WeatherIcons.frame(c, 0, size).sum() < WeatherIcons.frame(c, first, size).sum())
        }
    }

    @Test
    fun iconsAreCentredOnLargerMatrices() {
        val n = 25 // Phone (3)
        val px = WeatherIcons.frame(Condition.CLEAR_DAY, 0, n)
        val rows = (0 until n).filter { r -> (0 until n).any { c -> px[r * n + c] > 0 } }
        val cols = (0 until n).filter { c -> (0 until n).any { r -> px[r * n + c] > 0 } }
        assertEquals(rows.first(), n - 1 - rows.last())
        assertEquals(cols.first(), n - 1 - cols.last())
    }
}
