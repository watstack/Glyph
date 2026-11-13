package com.watstack.glyphweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationPickTest {
    private val now = 1_700_000_000_000L
    private val min = 60_000L

    @Test
    fun picksTheMostAccurateRecentFix() {
        val gps = Fix(-37.7712, 144.9601, 8f, now - 5 * min)
        val network = Fix(-37.77, 144.96, 40f, now - 1 * min)
        val approximate = Fix(-37.78, 144.97, 2_000f, now)
        assertEquals(gps, LocationPick.best(listOf(approximate, network, gps), now))
    }

    @Test
    fun ignoresStaleAndVagueFixes() {
        val stale = Fix(-33.87, 151.21, 5f, now - 31 * min) // where the phone was this morning
        val vague = Fix(-37.8, 145.0, 5_000f, now)
        val ok = Fix(-37.77, 144.96, 1_500f, now - 10 * min)
        assertEquals(ok, LocationPick.best(listOf(stale, vague, ok), now))
        assertNull(LocationPick.best(listOf(stale, vague), now))
        assertNull(LocationPick.best(emptyList(), now))
    }

    @Test
    fun unknownAccuracyAndFutureTimesAreNotTrusted() {
        assertNull(LocationPick.best(listOf(Fix(-37.77, 144.96, null, now)), now))
        assertNull(LocationPick.best(listOf(Fix(-37.77, 144.96, 5f, now + 10 * min)), now))
    }

    @Test
    fun sharedPointIsRoundedToTwoDecimals() {
        assertEquals(-37.77, LocationPick.round2(-37.77123), 1e-9)
        assertEquals(144.97, LocationPick.round2(144.96789), 1e-9)
        // Rounding moves the point less than the 1 km the plane search adds to its radius.
        assertEquals(true, Flights.distanceKm(-37.775, 144.965, LocationPick.round2(-37.775), LocationPick.round2(144.965)) < 1.0)
    }
}
