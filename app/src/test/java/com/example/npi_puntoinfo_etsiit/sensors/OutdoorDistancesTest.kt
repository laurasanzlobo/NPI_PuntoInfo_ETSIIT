package com.example.npi_puntoinfo_etsiit.sensors

import org.junit.Assert.*
import org.junit.Test

class OutdoorDistancesTest {
    @Test
    fun identicalCoordinatesHaveZeroDistance() {
        assertEquals(0.0, OutdoorDistances.distanceMeters(37.197, -3.6245, 37.197, -3.6245), 0.001)
    }

    @Test
    fun oneDegreeAtEquatorIsAbout111195Meters() {
        assertEquals(111_195.08, OutdoorDistances.distanceMeters(0.0, 0.0, 0.0, 1.0), 0.1)
    }

    @Test
    fun longitudeScaleDependsOnLatitude() {
        assertEquals(55_597.01, OutdoorDistances.distanceMeters(60.0, 0.0, 60.0, 1.0), 0.1)
    }

    @Test
    fun distanceIsSymmetricAndCrossesDateLineCorrectly() {
        val forward = OutdoorDistances.distanceMeters(37.197, -3.6245, 37.1828, -3.6057)
        val reverse = OutdoorDistances.distanceMeters(37.1828, -3.6057, 37.197, -3.6245)
        assertEquals(forward, reverse, 0.001)
        assertTrue(forward in 2_100.0..2_400.0)
        assertEquals(22_239.02, OutdoorDistances.distanceMeters(0.0, 179.9, 0.0, -179.9), 0.1)
    }

    @Test
    fun antipodalAndPolarCoordinatesRemainFinite() {
        assertEquals(20_015_114.44, OutdoorDistances.distanceMeters(0.0, 0.0, 0.0, 180.0), 0.1)
        assertEquals(0.0, OutdoorDistances.distanceMeters(90.0, -180.0, 90.0, 180.0), 0.001)
    }

    @Test
    fun eachExistingDiningHallIsNearestToItsOwnCoordinates() {
        CampusLocationManager.ugrDiningHalls.forEach { hall ->
            val nearest = OutdoorDistances.nearestDiningHall(hall.latitude, hall.longitude)
            assertEquals(hall, nearest?.hall)
            assertEquals(0.0, nearest!!.meters, 0.001)
        }
    }

    @Test
    fun distancesIncludeAllHallsAndAreSortedInMeters() {
        val distances = OutdoorDistances.distancesFrom(
            CampusLocationManager.ETSIIT_LATITUDE, CampusLocationManager.ETSIIT_LONGITUDE
        )
        assertEquals(CampusLocationManager.ugrDiningHalls.toSet(), distances.map { it.hall }.toSet())
        assertEquals("aynadamar", distances.first().hall.id)
        assertTrue(distances.first().meters < 20)
        assertTrue(distances.last().meters > 5_000)
        assertTrue(distances.zipWithNext().all { (a, b) -> a.meters <= b.meters })
    }

    @Test
    fun absentOrInvalidPositionDoesNotInventNearestHall() {
        val invalid = listOf(
            null to null, null to -3.6, 37.2 to null,
            Double.NaN to -3.6, 37.2 to Double.POSITIVE_INFINITY,
            90.01 to 0.0, -90.01 to 0.0, 0.0 to 180.01, 0.0 to -180.01
        )
        invalid.forEach { (latitude, longitude) ->
            assertTrue(OutdoorDistances.distancesFrom(latitude, longitude).isEmpty())
            assertNull(OutdoorDistances.nearestDiningHall(latitude, longitude))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun directDistanceRejectsInvalidDestination() {
        OutdoorDistances.distanceMeters(37.2, -3.6, Double.NaN, 0.0)
    }
}
