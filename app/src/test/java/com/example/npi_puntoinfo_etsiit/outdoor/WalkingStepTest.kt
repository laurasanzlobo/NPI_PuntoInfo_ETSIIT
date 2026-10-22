package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class WalkingStepTest {
    @Test fun allProviderTypesHaveSpecificManeuvers() {
        assertEquals(Maneuver.LEFT, Maneuver.fromOrsType(0))
        assertEquals(Maneuver.RIGHT, Maneuver.fromOrsType(1))
        assertEquals(Maneuver.STRAIGHT, Maneuver.fromOrsType(6))
        assertEquals(Maneuver.ROUNDABOUT, Maneuver.fromOrsType(7))
        assertEquals(Maneuver.ARRIVAL, Maneuver.fromOrsType(10))
        assertEquals(14, (0..13).map { Maneuver.fromOrsType(it) }.toSet().size)
    }

    @Test fun missingAndUnknownManeuversAreGenericNotFabricatedTurns() {
        assertEquals(Maneuver.UNKNOWN, Maneuver.fromOrsType(null))
        assertEquals(Maneuver.UNKNOWN, Maneuver.fromOrsType(999))
        assertEquals(Maneuver.UNKNOWN, WalkingStep("Indicación del proveedor", 3.0).maneuver)
    }

    @Test fun serviceInstructionsDistancesAndTypesArePreserved() {
        val response = """{"type":"FeatureCollection","features":[{
          "geometry":{"type":"LineString","coordinates":[[-3.62,37.19],[-3.63,37.20]]},
          "properties":{"summary":{"distance":25.5,"duration":30},"segments":[{"steps":[
            {"instruction":"Gira a la derecha","distance":25.5,"type":1},
            {"instruction":"Has llegado","distance":0,"type":10}]}]}}]}"""
        val route = (OpenRouteService.decode(RouteHttpResponse(200, response)) as WalkingRouteResult.Success).route
        assertEquals(WalkingStep("Gira a la derecha", 25.5, Maneuver.RIGHT), route.steps.first())
        assertEquals(WalkingStep("Has llegado", 0.0, Maneuver.ARRIVAL), route.steps.last())
    }
}
