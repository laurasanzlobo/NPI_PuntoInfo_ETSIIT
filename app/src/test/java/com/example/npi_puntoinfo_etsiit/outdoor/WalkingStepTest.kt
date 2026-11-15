package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class WalkingStepTest {
    @Test fun missingManeuverDefaultsToUnknown() {
        assertEquals(Maneuver.UNKNOWN, WalkingStep("Continúa recto", 3.0).maneuver)
    }

    @Test fun nearestStepIndexFindsClosestNode() {
        val route = WalkingRoute(
            points = listOf(GeoPoint(37.196873, -3.624742), GeoPoint(37.197846, -3.630084)),
            distanceMeters = 480.0,
            durationSeconds = 400.0,
            steps = listOf(
                WalkingStep("Sal de la ETSIIT", 240.0, Maneuver.DEPART, point = GeoPoint(37.196873, -3.624742)),
                WalkingStep("Has llegado al Edificio Auxiliar", 0.0, Maneuver.ARRIVAL, point = GeoPoint(37.197846, -3.630084))
            )
        )
        assertEquals(0, route.nearestStepIndex(GeoPoint(37.1969, -3.6248)))
        assertEquals(1, route.nearestStepIndex(GeoPoint(37.1978, -3.6300)))
        assertNull(route.nearestStepIndex(null))
        assertNull(route.nearestStepIndex(GeoPoint(Double.NaN, 0.0)))
    }
}
