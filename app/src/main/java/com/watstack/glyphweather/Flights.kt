package com.watstack.glyphweather

import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A plane near you, from an ADS-B feed, with its route when we could look it up. */
data class Flight(
    val hex: String,
    /** ICAO callsign as broadcast, e.g. "QFA401". */
    val callsign: String?,
    val registration: String?,
    /** ICAO type designator, e.g. "B738" or "A320". */
    val type: String?,
    val altitudeFt: Int?,
    /** Direction of travel in degrees clockwise from north. */
    val trackDeg: Double?,
    val distanceKm: Double,
    /** Airline flight number, e.g. "QF401" (from the route lookup). */
    val flightNumber: String? = null,
    /** Origin and destination IATA codes, e.g. "MEL" and "SYD". */
    val from: String? = null,
    val to: String? = null,
    /** Airline name, e.g. "Qantas" (from the route lookup). */
    val airline: String? = null,
    /** Ground speed in km/h, rounded to the nearest 10. */
    val speedKmh: Int? = null,
) {
    /**
     * The plane's info, in the order both the matrix and the app show it:
     * flight, route, aircraft type, airline, ground speed. Whatever we don't know is left out.
     */
    fun segments(): List<String> = listOfNotNull(
        flightNumber ?: callsign ?: registration ?: "PLANE",
        if (from != null && to != null) "$from>$to" else null,
        type,
        airline,
        speedKmh?.let { "$it km/h" },
    )

    /** The scrolling line on the matrix: "QF401  -  MEL>SYD  -  B738  -  Qantas  -  830 km/h". */
    fun ticker(): String = segments().joinToString(GAP)

    companion object {
        /** A wide gap with a dash between segments on the matrix. */
        const val GAP = "  -  "
    }
}

/** A route from adsbdb.com. */
data class Route(val flightNumber: String?, val from: String?, val to: String?, val airline: String?)

/**
 * Parsing and geometry for flights. Pure Kotlin plus org.json (no Android types) so it can be unit-tested on the JVM.
 */
object Flights {
    /** Planes closer than this are "overhead" and interrupt the weather. */
    const val RADIUS_KM = 5.0
    private const val KM_PER_KNOT = 1.852

    /**
     * The nearest airborne plane within [radiusKm] of ([lat], [lon]) in an adsb.lol / airplanes.live v2 response
     * (`{"ac": [{"hex", "flight", "r", "t", "alt_baro", "track", "lat", "lon"}, …]}`), or null.
     */
    fun nearest(body: String, lat: Double, lon: Double, radiusKm: Double = RADIUS_KM): Flight? {
        val ac = JSONObject(body).optJSONArray("ac") ?: return null
        var best: Flight? = null
        for (i in 0 until ac.length()) {
            val a = ac.optJSONObject(i) ?: continue
            val hex = a.optString("hex").takeIf { it.isNotBlank() } ?: continue
            if (!a.has("lat") || !a.has("lon")) continue
            // alt_baro is "ground" for planes taxiing or parked: not flying over.
            val alt = when (val v = a.opt("alt_baro")) {
                is Number -> v.toInt()
                "ground" -> continue
                else -> (a.opt("alt_geom") as? Number)?.toInt()
            }
            val d = distanceKm(lat, lon, a.getDouble("lat"), a.getDouble("lon"))
            if (d > radiusKm || (best != null && d >= best.distanceKm)) continue
            // gs is ground speed in knots.
            val speed = (a.opt("gs") as? Number)?.toDouble()?.let { Math.round(it * KM_PER_KNOT / 10.0).toInt() * 10 }
            val track = listOf("track", "true_heading", "mag_heading")
                .firstNotNullOfOrNull { k -> (a.opt(k) as? Number)?.toDouble() }
            best = Flight(
                hex = hex,
                callsign = text(a, "flight"),
                registration = text(a, "r"),
                type = text(a, "t"),
                altitudeFt = alt,
                trackDeg = track,
                distanceKm = d,
                speedKmh = speed,
            )
        }
        return best
    }

    /** A route from an adsbdb.com `/v0/callsign/{callsign}` response, or null if it doesn't know the callsign. */
    fun route(body: String): Route? {
        val route = JSONObject(body).optJSONObject("response")?.optJSONObject("flightroute") ?: return null
        fun iata(key: String) = route.optJSONObject(key)?.let { text(it, "iata_code") ?: text(it, "icao_code") }
        val airline = route.optJSONObject("airline")?.let { text(it, "name") }
        return Route(text(route, "callsign_iata"), iata("origin"), iata("destination"), airline)
    }

    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val a = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun text(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).trim().takeIf { it.isNotEmpty() }
}
