package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WalkingStepTest {
    @Test fun manualInstructionsPreserveManeuverAndUnknownDistance() {
        val waypoint = EtsiitAuxiliaryRoute.route.waypoints.first()
        val step = WalkingStep(waypoint.instruction, waypoint.distanceToNextMeters, waypoint.maneuver)
        assertEquals(Maneuver.DEPART, step.maneuver)
        assertEquals(58.0, step.distanceMeters!!, 0.0)
        assertEquals(waypoint.instruction, step.instruction)
        assertNull(WalkingStep("Distancia no registrada", null).distanceMeters)
    }
    @Test fun distanceFormatting() {
        assertEquals("25 m", formatOutdoorDistance(25.0))
        assertEquals("1.25 km", formatOutdoorDistance(1250.0, Locale.US))
    }
}
