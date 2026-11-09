package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GuidanceStepSelectionTest {
    private val data = Gson().fromJson(
        File("src/main/assets/guidance_exterior.json").readText(), GuidanceExteriorData::class.java
    )

    @Test fun auxiliarGuidanceCanStartAtAnyNodeInsteadOfTheDeparture() {
        assertEveryNodeCanBeSelected(data.ida)
    }

    @Test fun etsiitGuidanceCanStartAtAnyNodeUsingTheReturnPhotos() {
        assertEveryNodeCanBeSelected(data.vuelta)
    }

    @Test fun betweenNodesSelectsTheClosestEvenOutsideTheAdvanceRadius() {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(data.ida)
        val start = route.steps[6].point!!
        val end = route.steps[7].point!!
        val position = GeoPoint(
            start.latitude + (end.latitude - start.latitude) * 0.6,
            start.longitude + (end.longitude - start.longitude) * 0.6
        )
        assertTrue(OutdoorDistances.distanceMeters(
            position.latitude, position.longitude, end.latitude, end.longitude
        ) > 12.0)
        assertEquals(7, route.nearestStepIndex(position))
        assertEquals("rutas/ida/ida8.jpg", route.steps[route.nearestStepIndex(position)!!].photo)
    }

    @Test fun selectingAgainUsesTheNewPositionAndNotThePreviousNode() {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(data.ida)
        assertEquals(3, route.nearestStepIndex(route.steps[3].point))
        assertEquals(7, route.nearestStepIndex(route.steps[7].point))
        assertEquals(1, route.nearestStepIndex(route.steps[1].point))
    }

    @Test fun missingGpsDoesNotSelectANodeButALaterFixDoes() {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(data.vuelta)
        assertNull(route.nearestStepIndex(null))
        assertEquals(5, route.nearestStepIndex(route.steps[5].point))
    }

    @Test fun nodesWithoutPhotosAreNotReplacedByMoreDistantNodes() {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(data.vuelta)
        val entranceIndex = route.nearestStepIndex(route.steps[7].point)!!
        assertEquals(7, entranceIndex)
        assertNull(route.steps[entranceIndex].photo)
    }

    @Test fun missingOrInvalidNodeCoordinatesAreIgnoredWithoutChangingIndices() {
        val point = GeoPoint(37.19758, -3.625754)
        val route = WalkingRoute(emptyList(), 0.0, null, listOf(
            WalkingStep("Sin coordenadas", 0.0),
            WalkingStep("Coordenadas inválidas", 0.0, point = GeoPoint(Double.NaN, 0.0)),
            WalkingStep("Nodo válido", 0.0, point = point)
        ))
        assertEquals(2, route.nearestStepIndex(point))
        assertNull(route.copy(steps = route.steps.take(2)).nearestStepIndex(point))
    }

    @Test fun emptyRoutesAndInvalidGpsHaveNoClosestNode() {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(data.ida)
        assertNull(route.copy(steps = emptyList()).nearestStepIndex(route.steps[3].point))
        assertNull(route.nearestStepIndex(GeoPoint(Double.NaN, -3.625754)))
        assertNull(route.nearestStepIndex(GeoPoint(91.0, -3.625754)))
    }

    private fun assertEveryNodeCanBeSelected(tramo: GuidanceTramo) {
        val route = GuidanceExteriorRepository.tramoToWalkingRoute(tramo)
        tramo.nodos.forEachIndexed { index, node ->
            // Una fijación próxima, no exactamente igual a las coordenadas guardadas.
            val position = GeoPoint(node.lat + 0.00001, node.lng - 0.00001)
            val selectedIndex = route.nearestStepIndex(position)!!
            assertEquals(node.id, index, selectedIndex)
            val selected = route.steps[selectedIndex]
            assertEquals(node.id, selected.nodeId)
            assertEquals(node.foto, selected.photo)
            assertEquals(node.rumbo, selected.heading)
            assertEquals(node.instruccion, selected.instruction)
        }
    }
}
