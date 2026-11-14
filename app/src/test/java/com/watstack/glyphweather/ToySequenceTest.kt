package com.watstack.glyphweather

import com.watstack.glyphweather.ToySequence.Phase
import com.watstack.glyphweather.WeatherIcons.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToySequenceTest {
    private val size = 13
    private var live = WeatherState(18.0, Condition.RAIN, 2.0)
    private val plane = Flight("7c6b2d", "QFA401", "VH-VZA", "B738", 35_000, 47.0, 2.4, "QF401", "MEL", "SYD", "Qantas", 830)

    /** Advances through to the next change of phase, returning the phases seen with how many steps and ms each took. */
    private fun run(seq: ToySequence, maxSteps: Int = 1000): List<Triple<Phase, Int, Long>> {
        val out = mutableListOf<Triple<Phase, Int, Long>>()
        repeat(maxSteps) {
            val p = seq.phase
            val ms = seq.delayMs()
            seq.pixels(size) // never throws in any phase
            if (out.isNotEmpty() && out.last().first == p) {
                val (_, n, total) = out.last()
                out[out.size - 1] = Triple(p, n + 1, total + ms)
            } else {
                out += Triple(p, 1, ms)
            }
            seq.advance()
        }
        return out
    }

    @Test
    fun planeRunsCrossingThenScrollThenAltitudeThenTemperature() {
        val seq = ToySequence({ live })
        seq.startPlane(plane, size)
        val phases = run(seq, 1000).take(4)
        val scrollSteps = DigitFont.scrollSteps(plane.ticker(), size) * 4 // the info scrolls past 4 times
        assertEquals(Triple(Phase.CROSSING, 32, 3_200L), phases[0])
        assertEquals(Triple(Phase.SCROLL, scrollSteps, scrollSteps * 80L), phases[1])
        assertEquals(Triple(Phase.ALTITUDE, 1, 3_000L), phases[2])
        assertEquals(Phase.TEMPERATURE, phases[3].first)
        assertFalse(seq.isPlane)
        val total = phases.take(3).sumOf { it.third }
        val seq2 = ToySequence({ live }).apply { startPlane(plane, size) }
        assertEquals("planeDurationMs matches the steps", total, seq2.planeDurationMs())
        assertTrue("about a minute, not more ($total ms)", total in 30_000L..75_000L)
    }

    @Test
    fun eachPassStartsBlankWithTheTextComingInFromTheRight() {
        val seq = ToySequence({ live })
        seq.startPlane(plane, size)
        repeat(32) { seq.advance() }
        val onePass = DigitFont.scrollSteps(plane.ticker(), size)
        for (pass in 0 until 4) {
            assertEquals(Phase.SCROLL, seq.phase)
            assertTrue("pass $pass starts blank", seq.pixels(size).none { it > 0 })
            val passFrames = (0 until onePass).map { seq.pixels(size).also { seq.advance() } }
            assertTrue("pass $pass shows text", passFrames.any { px -> px.any { it > 0 } })
        }
        assertEquals(Phase.ALTITUDE, seq.phase)
    }

    @Test
    fun scrollShowsTheFlightInfo() {
        val seq = ToySequence({ live })
        seq.startPlane(plane, size)
        repeat(32 + size) { seq.advance() } // past the crossing, text fully on screen
        assertEquals(Phase.SCROLL, seq.phase)
        val px = seq.pixels(size)
        assertTrue("text is lit", px.any { it > 0 })
    }

    @Test
    fun sunIsFollowedByUvAndRainIsNot() {
        live = WeatherState(25.0, Condition.CLEAR_DAY, 8.0)
        val sunny = run(ToySequence({ live }), 30).map { it.first }
        assertEquals(listOf(Phase.TEMPERATURE, Phase.ICON, Phase.UV, Phase.TEMPERATURE), sunny.take(4))

        live = WeatherState(12.0, Condition.RAIN, 1.0)
        val rainy = run(ToySequence({ live }), 30).map { it.first }
        assertEquals(listOf(Phase.TEMPERATURE, Phase.ICON, Phase.TEMPERATURE), rainy.take(3))
    }

    @Test
    fun oneOffSimulationReturnsToLiveWeather() {
        val seq = ToySequence({ live })
        val storm = WeatherState(20.0, Condition.THUNDERSTORM, null)
        seq.simulateWeather(storm, repeat = false)
        assertEquals(storm, seq.weather)
        assertEquals(Phase.ICON, seq.phase)
        repeat(WeatherIcons.FRAMES) { seq.advance() }
        assertEquals(Phase.TEMPERATURE, seq.phase)
        assertEquals("simulated temperature still showing", storm, seq.weather)
        seq.advance()
        assertEquals(live, seq.weather)
    }

    @Test
    fun repeatingSimulationKeepsGoing() {
        val seq = ToySequence({ live })
        val snow = WeatherState(-2.0, Condition.SNOW, null)
        seq.simulateWeather(snow, repeat = true)
        repeat(100) { seq.advance() }
        assertEquals(snow, seq.weather)
        seq.stopSimulation()
        assertEquals(live, seq.weather)
    }

    @Test
    fun restartClearsAPlane() {
        val seq = ToySequence({ live })
        seq.startPlane(plane, size)
        seq.restart(atIcon = true)
        assertFalse(seq.isPlane)
        assertNull(seq.flight)
        assertEquals(Phase.ICON, seq.phase)
    }

    @Test
    fun noWeatherYetStaysOnTemperature() {
        live = WeatherState(null, null, null)
        val seq = ToySequence({ live })
        seq.restart(atIcon = true)
        assertEquals(Phase.TEMPERATURE, seq.phase)
        seq.advance()
        assertEquals(Phase.TEMPERATURE, seq.phase)
    }

    @Test
    fun simulationWaitsAMinuteForTheFlip() {
        Simulation.listener = null
        val req = Simulation.Plane(plane)
        assertFalse(Simulation.send(req, now = 1_000))
        assertEquals(req, Simulation.take(now = 30_000))
        assertNull("taken once", Simulation.take(now = 30_000))
        Simulation.send(req, now = 1_000)
        assertNull("too old", Simulation.take(now = 1_000 + Simulation.MAX_AGE_MS + 1))
        Simulation.listener = { true }
        assertTrue(Simulation.send(req, now = 0))
        assertNull(Simulation.take(now = 0))
        Simulation.listener = null
    }
}
