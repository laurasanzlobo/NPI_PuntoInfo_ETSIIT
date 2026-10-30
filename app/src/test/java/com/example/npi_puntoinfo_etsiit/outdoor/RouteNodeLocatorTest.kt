package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class RouteNodeLocatorTest {
    private val route = EtsiitAuxiliaryRoute.route
    private val locator = RouteNodeLocator(route)

    @Test fun allFortyEightStoredCoordinatesHaveAnExplicitStepAndUniqueId() {
        assertEquals(48, locator.nodes.size)
        assertEquals(48, locator.nodes.map { it.id }.distinct().size)
        assertEquals(route.waypoints.indices.toSet(), locator.nodes.map { it.stepIndex }.toSet())
        route.waypoints.forEachIndexed { index, waypoint ->
            assertEquals(index, locator.nearest(waypoint.point!!)!!.node.stepIndex)
        }
    }

    @Test fun preservesAuxiliaryToEtsiitDirectionAndAllCoordinatesWithoutInventingNodes() {
        assertEquals("auxiliary-etsiit", route.id)
        assertEquals(GeoPoint(37.1978519, -3.6301486), locator.nodes.first().point)
        assertEquals(GeoPoint(37.1967175, -3.6247391), locator.nodes.last().point)
        assertFalse(route.fieldValidated)
        assertTrue(route.cartographyVerified)
        assertEquals(route.waypoints.flatMap { it.pathToNext }.toSet(), locator.nodes.map { it.point }.toSet())
        assertTrue(locator.nodes.all { it.point.isValid })
    }

    @Test fun nearestIsTheMinimumHaversineDistanceNotASegmentOrSearchRadius() {
        listOf(GeoPoint(37.19755, -3.6280), GeoPoint(37.1979, -3.626), GeoPoint(37.1988, -3.631)).forEach { point ->
            val expected = locator.nodes.minBy { RouteGeometry.distance(point, it.point) }
            val result = locator.nearest(point)!!
            assertEquals(expected, result.node)
            assertEquals(RouteGeometry.distance(point, expected.point), result.distanceMeters, 0.001)
        }
    }

    @Test fun aJunctionBelongsToTheInstructionStartingThere() {
        for (index in 1 until route.waypoints.lastIndex) {
            val node = locator.nearest(route.waypoints[index].point!!)!!.node
            assertEquals(index, node.stepIndex)
        }
    }

    @Test fun longRoadContainsSeveralNodesForTheSameStep() {
        val road = locator.nodes.filter { it.stepIndex == 1 }
        assertTrue(road.size > 2)
        road.forEach { assertEquals(1, locator.nearest(it.point)!!.node.stepIndex) }
    }

    @Test fun unconfiguredRouteHasNoGpsLookup() {
        val lookup = RouteNodeLocator(SyntheticRoute.route.copy(fieldValidated = false))
        assertTrue(lookup.nodes.isEmpty())
        assertNull(lookup.nearest(GeoPoint(0.0, 0.0)))
    }

    @Test fun geographicalDistanceAndProjectionRemainAvailableForEstimates() {
        val a = SyntheticRoute.point(0.0, 0.0)
        val b = SyntheticRoute.point(50.0, 0.0)
        assertEquals(50.0, RouteGeometry.distance(a, b), 0.01)
        assertEquals(0.0, RouteGeometry.distance(a, a), 0.001)
        val projected = RouteGeometry.project(SyntheticRoute.point(25.0, 10.0), a, b)
        assertEquals(0.5, projected.fraction, 0.001)
        assertEquals(10.0, projected.distanceMeters, 0.01)
    }
}
