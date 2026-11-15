package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class OutdoorRoutingTest {
    private val origin = OutdoorDestinations.find("etsiit")!!.point

    @Test fun catalogReusesEveryDiningCoordinateAndId() {
        CampusLocationManager.ugrDiningHalls.forEach { hall ->
            val outdoor = OutdoorDestinations.forDiningHall(hall.id)!!
            assertEquals(hall.name, outdoor.name)
            assertEquals(hall.latitude, outdoor.point.latitude, 0.0)
            assertEquals(hall.longitude, outdoor.point.longitude, 0.0)
        }
        assertEquals(OutdoorDestinations.all.size, OutdoorDestinations.all.map { it.id }.toSet().size)
        assertTrue(OutdoorDestinations.all.all { it.point.isValid })
    }

    @Test fun catalogReusesEtsiitCoordinates() {
        assertEquals(CampusLocationManager.ETSIIT_LATITUDE, origin.latitude, 0.0)
        assertEquals(CampusLocationManager.ETSIIT_LONGITUDE, origin.longitude, 0.0)
    }

    @Test fun unknownDestinationNeverFallsBackToDifferentBuilding() {
        assertNull(OutdoorDestinations.find("unknown"))
        assertNull(OutdoorDestinations.find(null))
        assertNull(OutdoorDestinations.forDiningHall("unknown"))
    }

    @Test fun outdoorRouteNeverUsesIndoorNamespace() {
        OutdoorDestinations.all.forEach {
            assertEquals("outdoor?destinationId=${it.id}", OutdoorNavigationRoute.create(it.id))
            assertFalse(OutdoorNavigationRoute.create(it.id).startsWith("navigation"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun routeRejectsQueryInjection() { OutdoorNavigationRoute.create("id&destination=Aula") }

    @Test fun distancesAndDurationsUseCorrectUnits() {
        assertEquals("0 m", formatOutdoorDistance(0.0))
        assertEquals("999 m", formatOutdoorDistance(999.0))
        assertEquals("1.00 km", formatOutdoorDistance(1000.0, Locale.US))
        assertEquals("2,35 km", formatOutdoorDistance(2345.6, Locale.forLanguageTag("es-ES")))
        assertEquals("1 min", formatWalkingDuration(1.0))
        assertEquals("31 min", formatWalkingDuration(1801.0))
        assertEquals("1 h 1 min", formatWalkingDuration(3601.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDistanceCannotBeFormattedAsRealResult() { formatOutdoorDistance(Double.NaN) }
}
