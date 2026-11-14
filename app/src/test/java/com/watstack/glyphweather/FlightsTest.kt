package com.watstack.glyphweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightsTest {
    // Brunswick, VIC
    private val lat = -37.77
    private val lon = 144.96

    private val feed = """
        {"ac": [
          {"hex": "7c6b2d", "flight": "QFA401  ", "r": "VH-VZA", "t": "B738", "alt_baro": 12025, "track": 47.3, "gs": 450.2,
           "lat": -37.75, "lon": 144.97},
          {"hex": "7c1234", "flight": "JST510  ", "t": "A320", "alt_baro": 3000, "track": 180.0,
           "lat": -37.80, "lon": 144.96},
          {"hex": "7c9999", "flight": "VOZ800", "t": "B738", "alt_baro": "ground", "lat": -37.77, "lon": 144.96},
          {"hex": "7c0000", "t": "C172", "alt_baro": 1500, "lat": -38.20, "lon": 144.96},
          {"hex": "7cffff", "flight": "NOPOS"}
        ],
        "msg": "No error", "now": 1731456000000, "total": 5}
    """.trimIndent()

    @Test
    fun picksNearestAirbornePlaneInRange() {
        val f = Flights.nearest(feed, lat, lon)!!
        assertEquals("7c6b2d", f.hex)
        assertEquals("QFA401", f.callsign)
        assertEquals("VH-VZA", f.registration)
        assertEquals("B738", f.type)
        assertEquals(12025, f.altitudeFt)
        assertEquals(47.3, f.trackDeg!!, 0.001)
        assertEquals(830, f.speedKmh) // 450.2 kt = 833.8 km/h, to the nearest 10
        assertTrue(f.distanceKm < 3)
    }

    @Test
    fun ignoresPlanesOnTheGroundAndOutOfRange() {
        val onlyGround = """{"ac": [{"hex": "a", "alt_baro": "ground", "lat": -37.77, "lon": 144.96}]}"""
        assertNull(Flights.nearest(onlyGround, lat, lon))
        val farAway = """{"ac": [{"hex": "a", "alt_baro": 30000, "lat": -37.90, "lon": 144.96}]}"""
        assertNull(Flights.nearest(farAway, lat, lon))
        assertNull(Flights.nearest("""{"ac": []}""", lat, lon))
        assertNull(Flights.nearest("""{"msg": "No error"}""", lat, lon))
    }

    @Test
    fun handlesMissingFields() {
        val bare = """{"ac": [{"hex": "abc123", "lat": -37.77, "lon": 144.97}]}"""
        val f = Flights.nearest(bare, lat, lon)!!
        assertNull(f.callsign)
        assertNull(f.type)
        assertNull(f.altitudeFt)
        assertNull(f.trackDeg)
        assertNull(f.speedKmh)
        assertEquals("PLANE", f.ticker())
    }

    @Test
    fun parsesAdsbdbRoute() {
        val body = """
            {"response": {"flightroute": {"callsign": "QFA401", "callsign_icao": "QFA401", "callsign_iata": "QF401",
              "airline": {"name": "Qantas", "icao": "QFA", "iata": "QF"},
              "origin": {"iata_code": "MEL", "icao_code": "YMML", "name": "Melbourne Airport"},
              "destination": {"iata_code": "SYD", "icao_code": "YSSY", "name": "Sydney Airport"}}}}
        """.trimIndent()
        assertEquals(Route("QF401", "MEL", "SYD", "Qantas"), Flights.route(body))
        assertNull(Flights.route("""{"response": "unknown callsign"}"""))
    }

    @Test
    fun tickerLeavesOutWhatIsUnknown() {
        val base = Flight("a", "QFA401", "VH-VZA", "B738", 35000, 0.0, 1.0)
        assertEquals("QFA401  -  B738", base.ticker())
        val full = base.copy(flightNumber = "QF401", from = "MEL", to = "SYD", airline = "Qantas", speedKmh = 830)
        assertEquals(listOf("QF401", "MEL>SYD", "B738", "Qantas", "830 km/h"), full.segments())
        assertEquals("QF401  -  MEL>SYD  -  B738  -  Qantas  -  830 km/h", full.ticker())
        assertEquals("VH-VZA", base.copy(callsign = null, type = null).ticker())
        assertEquals("route needs both ends", listOf("QF401", "B738"), base.copy(flightNumber = "QF401", from = "MEL").segments())
    }

    @Test
    fun distanceIsGreatCircle() {
        // Melbourne to Sydney airports: about 705 km.
        assertEquals(705.0, Flights.distanceKm(-37.6733, 144.8433, -33.9461, 151.1772), 5.0)
        assertEquals(0.0, Flights.distanceKm(lat, lon, lat, lon), 1e-9)
    }
}
