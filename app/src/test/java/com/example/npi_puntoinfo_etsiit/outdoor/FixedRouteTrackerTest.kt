package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates
import org.junit.Assert.*
import org.junit.Test

/** Coordenadas sintéticas solo para pruebas, nunca son posiciones del itinerario de Granada. */
internal object SyntheticRoute {
    private const val metersPerDegree = 111_195.0802335
    fun point(x: Double, y: Double) = GeoPoint(y / metersPerDegree, x / metersPerDegree)
    fun fix(x: Double, y: Double, time: Long, accuracy: Float = 2f) =
        DeviceCoordinates(point(x, y).latitude, point(x, y).longitude, accuracy, time)
    val route = FixedOutdoorRoute("synthetic", "Solo pruebas", listOf(
        RouteWaypoint("a", point(0.0, 0.0), "Salida simulada", Maneuver.DEPART),
        RouteWaypoint("b", point(50.0, 0.0), "Giro simulado", Maneuver.LEFT),
        RouteWaypoint("c", point(50.0, 50.0), "Llegada simulada", Maneuver.ARRIVAL)
    ), true)
}

class FixedRouteTrackerTest {
    private val tracker = FixedRouteTracker(SyntheticRoute.route).apply { start() }
    private var time = 1_000L
    private fun observe(x: Double, y: Double, accuracy: Float = 2f): GuidanceState {
        val result = tracker.observe(SyntheticRoute.fix(x, y, time, accuracy), time)
        time += 2_000
        return result
    }

    @Test fun firstFixImmediatelySelectsTheNearestStepAtEveryStoredNode() {
        val route = EtsiitAuxiliaryRoute.route
        RouteNodeLocator(route).nodes.forEach { node ->
            val actual = FixedRouteTracker(route).apply { start() }
            val result = actual.observe(DeviceCoordinates(node.point.latitude, node.point.longitude, 5f, 1_000), 1_000)
            assertTrue("Nodo ${node.id}", result.positionConfirmed)
            assertEquals(node.stepIndex.coerceAtMost(route.waypoints.lastIndex - 1), result.stepIndex)
            assertEquals(ProgressSource.GPS, result.source)
            assertEquals(node.id, result.nearestNodeId)
            assertFalse(result.arrived)
            assertNull(result.suggestedStepIndex) // La selección inicial sigue siendo inmediata.
        }
    }

    @Test fun firstMidwayFixDoesNotWaitForThreeReadingsOrForTheOrigin() {
        val route = EtsiitAuxiliaryRoute.route
        val actual = FixedRouteTracker(route).apply { start() }
        val point = route.waypoints[1].pathToNext[5]
        actual.observe(DeviceCoordinates(point.latitude, point.longitude, 25f, 1_000), 1_000)
        assertTrue(actual.state.positionConfirmed)
        assertEquals(1, actual.state.stepIndex)
        assertFalse(actual.state.gpsProblem)
        assertTrue(actual.state.gpsMessage.contains("nodo más cercano"))
    }

    @Test fun positionOutsideOldCorridorStillGetsNearestInstruction() {
        // Antes, error de 25 m + desplazamiento de 20 m impedían localizar cualquier tramo.
        val result = observe(50.0, -20.0, 25f)
        assertTrue(result.positionConfirmed)
        assertEquals(1, result.stepIndex)
        assertNull(result.remainingMeters) // No inventar una distancia caminable fuera del corredor.
    }

    @Test fun lowAccuracyShowsApproximateStepInsteadOfSearchingForever() {
        val result = observe(50.0, 0.0, 120f)
        assertTrue(result.positionConfirmed)
        assertEquals(1, result.stepIndex)
        assertTrue(result.gpsMessage.contains("poco precisa"))
        assertNull(result.remainingMeters)
        assertFalse(result.arrived)
    }

    @Test fun recentCachedPositionUpToThirtySecondsStillSelectsANode() {
        tracker.observe(SyntheticRoute.fix(50.0, 0.0, 1_000), 30_000)
        assertTrue(tracker.state.positionConfirmed)
        assertEquals(1, tracker.state.stepIndex)
    }

    @Test fun invalidOldFutureMissingTimeAndNegativeAccuracyAreRejectedWithAnExplanation() {
        val invalid = listOf(
            SyntheticRoute.fix(0.0, 0.0, 0),
            SyntheticRoute.fix(0.0, 0.0, 100_001),
            SyntheticRoute.fix(0.0, 0.0, 100_000).copy(latitude = Double.NaN),
            SyntheticRoute.fix(0.0, 0.0, 100_000).copy(longitude = 200.0),
            SyntheticRoute.fix(0.0, 0.0, 100_000).copy(accuracyMeters = -1f),
            SyntheticRoute.fix(0.0, 0.0, 100_000).copy(elapsedRealtimeMillis = null),
            SyntheticRoute.fix(0.0, 0.0, 100_000).copy(elapsedRealtimeMillis = -1)
        )
        invalid.forEach {
            val actual = FixedRouteTracker(SyntheticRoute.route).apply { start() }
            actual.observe(it, 100_000)
            assertFalse(actual.state.positionConfirmed)
            assertTrue(actual.state.gpsProblem)
            assertNull(actual.state.remainingMeters)
        }
    }

    @Test fun singlePreciseJumpDoesNotReplaceTheCurrentInstruction() {
        observe(0.0, 0.0)
        observe(50.0, 50.0)
        assertEquals(0, tracker.state.stepIndex)
        assertTrue(tracker.state.gpsMessage.contains("Salto"))
        assertFalse(tracker.state.arrived)
    }

    @Test fun boundaryOscillationKeepsCurrentStepUntilOtherNodeIsClearlyCloserTwice() {
        observe(0.0, 0.0)
        for (x in 5..30 step 5) observe(x.toDouble(), 0.0)
        observe(25.0, 0.0)
        observe(30.0, 0.0)
        assertEquals(0, tracker.state.stepIndex)
        observe(35.0, 0.0)
        assertEquals(0, tracker.state.stepIndex)
        assertEquals(1, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(1, tracker.state.stepIndex)
        repeat(4) { observe(if (it % 2 == 0) 24.0 else 26.0, 0.0) }
        assertEquals(1, tracker.state.stepIndex)
    }

    @Test fun repeatedOrOutOfOrderTimestampsDoNotConfirmAStepChange() {
        observe(0.0, 0.0)
        for (x in 5..25 step 5) observe(x.toDouble(), 0.0)
        val stamp = time
        val fix = SyntheticRoute.fix(35.0, 0.0, stamp)
        repeat(10) { tracker.observe(fix, stamp) }
        tracker.observe(SyntheticRoute.fix(35.0, 0.0, stamp - 1_000), stamp)
        assertEquals(0, tracker.state.stepIndex)
    }

    @Test fun resumingOrLongGapRequiresConfirmationBeforeChangingAKnownStep() {
        observe(0.0, 0.0)
        tracker.pauseGps()
        assertNull(tracker.state.remainingMeters)
        observe(50.0, 25.0)
        observe(50.0, 25.0)
        assertEquals(0, tracker.state.stepIndex)
        assertEquals(1, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(1, tracker.state.stepIndex)
        assertTrue(tracker.state.positionConfirmed)
        time += tracker.policy.maxReadingGapMillis + 1
        observe(0.0, 0.0)
        observe(0.0, 0.0)
        assertEquals(1, tracker.state.stepIndex)
        assertEquals(0, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(0, tracker.state.stepIndex)
        assertFalse(tracker.state.arrived)
    }

    @Test fun manualStepIsPreservedUntilGpsIsExplicitlyReenabled() {
        tracker.next()
        repeat(5) { observe(0.0, 0.0) }
        assertEquals(1, tracker.state.stepIndex)
        assertEquals(ProgressSource.MANUAL, tracker.state.source)
        tracker.resumeGps()
        observe(0.0, 0.0)
        observe(0.0, 0.0)
        assertEquals(1, tracker.state.stepIndex)
        assertEquals(0, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(0, tracker.state.stepIndex)
        assertEquals(ProgressSource.GPS, tracker.state.source)
    }

    @Test fun considerableDeviationWarnsButStillShowsTheNearestStep() {
        repeat(3) { observe(1000.0, 1000.0) }
        assertTrue(tracker.state.positionConfirmed)
        assertTrue(tracker.state.offRoute)
        assertFalse(tracker.state.arrived)
        assertNull(tracker.state.remainingMeters)
        tracker.pauseGps()
        repeat(3) { observe(0.0, 0.0) }
        assertFalse(tracker.state.offRoute)
    }

    @Test fun nearestFinalNodeDoesNotDeclareArrivalOnOneReadingOrWithLowAccuracy() {
        val first = observe(50.0, 50.0)
        assertEquals(1, first.stepIndex) // Mantener la indicación del acceso hasta confirmar llegada.
        assertFalse(first.arrived)
        repeat(4) { observe(50.0, 50.0, 20f) }
        assertFalse(tracker.state.arrived)
        observe(50.0, 50.0)
        observe(50.0, 50.0)
        assertFalse(tracker.state.arrived)
        observe(50.0, 50.0)
        assertFalse(tracker.state.arrived)
        assertEquals(2, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertTrue(tracker.state.arrived)
        assertEquals(0.0, tracker.state.remainingMeters!!, 0.0)
    }

    @Test fun oldButUsablePositionsSelectStepButNeverConfirmArrival() {
        repeat(5) { i ->
            val stamp = 1_000L + i * 2_000
            tracker.observe(SyntheticRoute.fix(50.0, 50.0, stamp), stamp + 20_000)
        }
        assertTrue(tracker.state.positionConfirmed)
        assertFalse(tracker.state.arrived)
    }

    @Test fun remainingDistanceUsesProjectionOnlyAfterNodeHasSelectedTheStep() {
        observe(25.0, 0.0)
        assertEquals(0, tracker.state.stepIndex)
        assertEquals(75.0, tracker.state.remainingMeters!!, 0.1)
    }

    @Test fun manualControlsRestartAndFinishWorkWithoutGps() {
        val manual = FixedRouteTracker(SyntheticRoute.route)
        manual.next()
        assertEquals(0, manual.state.stepIndex)
        manual.start(); manual.next()
        assertEquals(1, manual.state.stepIndex)
        manual.next()
        assertTrue(manual.state.arrived)
        manual.previous()
        assertFalse(manual.state.arrived)
        manual.previous(); manual.previous()
        assertEquals(0, manual.state.stepIndex)
        manual.restart()
        assertEquals(GuidanceState(), manual.state)
        manual.start(); manual.finish()
        assertEquals(GuidanceState(), manual.state)
    }

    @Test fun unconfiguredRouteCannotUseGps() {
        val actual = FixedRouteTracker(SyntheticRoute.route.copy(fieldValidated = false)).apply { start() }
        actual.observe(SyntheticRoute.fix(0.0, 0.0, 1_000), 1_000)
        assertFalse(actual.state.positionConfirmed)
        assertTrue(actual.state.gpsProblem)
    }

    @Test fun simulatedWalkAlongTheRealRouteAsksBeforeEveryStepAndArrival() {
        val route = EtsiitAuxiliaryRoute.route
        val actual = FixedRouteTracker(route).apply { start() }
        var stamp = 1_000L
        val visited = mutableSetOf<Int>()
        var confirmations = 0
        fun accept(point: GeoPoint) {
            val previousStep = actual.state.stepIndex
            actual.observe(DeviceCoordinates(point.latitude, point.longitude, 2f, stamp), stamp)
            stamp += 2_000
            assertEquals(previousStep, actual.state.stepIndex)
            if (actual.state.suggestedStepIndex != null) {
                actual.confirmSuggestedStep()
                confirmations++
            }
            assertTrue(actual.state.positionConfirmed)
            assertFalse(actual.state.offRoute)
            visited += actual.state.stepIndex
        }
        route.waypoints.dropLast(1).forEach { waypoint ->
            waypoint.pathToNext.zipWithNext().forEach { (a, b) ->
                val samples = kotlin.math.ceil(RouteGeometry.distance(a, b) / 2.5).toInt().coerceAtLeast(1)
                for (i in 0..samples) {
                    val t = i.toDouble() / samples
                    accept(GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
                }
            }
        }
        repeat(3) { accept(route.waypoints.last().point!!) }
        assertEquals(route.waypoints.indices.toSet(), visited)
        assertEquals(route.waypoints.lastIndex, confirmations)
        assertTrue(actual.state.arrived)
    }

    private fun approachNextStep() {
        observe(0.0, 0.0)
        for (x in 5..40 step 5) observe(x.toDouble(), 0.0)
        assertEquals(1, tracker.state.suggestedStepIndex)
        assertEquals(0, tracker.state.stepIndex)
    }

    @Test fun detectionProposesButNeverChangesTheCurrentInstructionWithoutConsent() {
        approachNextStep()
        repeat(6) { observe(40.0, 0.0) }
        assertEquals(0, tracker.state.stepIndex)
        assertEquals(1, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(1, tracker.state.stepIndex)
        assertEquals(ProgressSource.GPS, tracker.state.source)
        assertNull(tracker.state.suggestedStepIndex)
    }

    @Test fun confirmingKeepsGpsEnabledSoItCanProposeTheFollowingStep() {
        approachNextStep()
        tracker.confirmSuggestedStep()
        observe(45.0, 0.0)
        observe(50.0, 0.0)
        for (y in 5..50 step 5) observe(50.0, y.toDouble())
        repeat(3) { observe(50.0, 50.0) }
        assertEquals(1, tracker.state.stepIndex)
        assertFalse(tracker.state.arrived)
        assertEquals(2, tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertTrue(tracker.state.arrived)
    }

    @Test fun decliningKeepsCurrentStepAndDoesNotRepeatTheSamePrompt() {
        approachNextStep()
        tracker.dismissSuggestedStep()
        assertNull(tracker.state.suggestedStepIndex)
        repeat(12) { observe(40.0, 0.0) }
        assertEquals(0, tracker.state.stepIndex)
        assertNull(tracker.state.suggestedStepIndex)
        assertEquals(setOf(1), tracker.dismissedSuggestionSteps())
    }

    @Test fun declinedSuggestionSurvivesRestorationWithoutRestoringGpsEvidence() {
        approachNextStep()
        tracker.dismissSuggestedStep()
        val restored = FixedRouteTracker(SyntheticRoute.route).apply {
            start()
            restoreSource(tracker.state.source)
            restoreDismissedSuggestions(tracker.dismissedSuggestionSteps())
        }
        repeat(4) { i ->
            val stamp = 30_000L + i * 2_000
            restored.observe(SyntheticRoute.fix(40.0, 0.0, stamp), stamp)
        }
        assertEquals(0, restored.state.stepIndex)
        assertNull(restored.state.suggestedStepIndex)
    }

    @Test fun returningToTheCurrentNodeWithdrawsAnObsoleteProposal() {
        approachNextStep()
        for (x in 35 downTo 10 step 5) observe(x.toDouble(), 0.0)
        assertEquals(0, tracker.state.stepIndex)
        assertNull(tracker.state.suggestedStepIndex)
    }

    @Test fun pausingOrReceivingInvalidGpsCancelsPendingConsent() {
        approachNextStep()
        tracker.pauseGps()
        tracker.confirmSuggestedStep() // Un clic tardío no puede aceptar una propuesta retirada.
        assertEquals(0, tracker.state.stepIndex)
        assertNull(tracker.state.suggestedStepIndex)
        repeat(2) { observe(40.0, 0.0) }
        assertEquals(1, tracker.state.suggestedStepIndex)
        observe(40.0, 0.0, Float.NaN)
        assertNull(tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(0, tracker.state.stepIndex)
    }

    @Test fun changingStepManuallyClearsProposalAndReenablesItForANewContext() {
        approachNextStep()
        tracker.dismissSuggestedStep()
        tracker.next()
        assertEquals(1, tracker.state.stepIndex)
        assertNull(tracker.state.suggestedStepIndex)
        assertTrue(tracker.dismissedSuggestionSteps().isEmpty())
        tracker.previous()
        tracker.resumeGps()
        repeat(2) { observe(40.0, 0.0) }
        assertEquals(1, tracker.state.suggestedStepIndex)
    }

    @Test fun changingNodesInsideTheSameInstructionNeverShowsAPrompt() {
        val route = EtsiitAuxiliaryRoute.route
        val actual = FixedRouteTracker(route).apply { start() }
        val points = route.waypoints[1].pathToNext
        var stamp = 1_000L
        points.drop(2).take(3).forEach { point ->
            actual.observe(DeviceCoordinates(point.latitude, point.longitude, 25f, stamp), stamp)
            stamp += 2_000
            assertEquals(1, actual.state.stepIndex)
            assertNull(actual.state.suggestedStepIndex)
        }
    }

    @Test fun decliningArrivalDoesNotMarkItOrRepeatItsPopup() {
        repeat(3) { observe(50.0, 50.0) }
        assertEquals(2, tracker.state.suggestedStepIndex)
        assertFalse(tracker.state.arrived)
        tracker.dismissSuggestedStep()
        repeat(8) { observe(50.0, 50.0) }
        assertEquals(1, tracker.state.stepIndex)
        assertFalse(tracker.state.arrived)
        assertNull(tracker.state.suggestedStepIndex)
    }

    @Test fun lostArrivalPrecisionWithdrawsTheProposalAndLateConfirmationDoesNothing() {
        repeat(3) { observe(50.0, 50.0) }
        assertEquals(2, tracker.state.suggestedStepIndex)
        observe(50.0, 50.0, 25f)
        assertNull(tracker.state.suggestedStepIndex)
        tracker.confirmSuggestedStep()
        assertEquals(1, tracker.state.stepIndex)
        assertFalse(tracker.state.arrived)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholdsRejected() { TrackingPolicy(requiredReadings = 1) }
    @Test(expected = IllegalArgumentException::class)
    fun invalidSwitchMarginRejected() { TrackingPolicy(nodeSwitchMarginMeters = -1.0) }
    @Test(expected = IllegalArgumentException::class)
    fun invalidCoordinatesCannotBeMarkedValidated() {
        SyntheticRoute.route.copy(waypoints = SyntheticRoute.route.waypoints.mapIndexed { index, waypoint ->
            if (index == 0) waypoint.copy(point = null) else waypoint
        })
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateWaypointIdsRejected() {
        SyntheticRoute.route.copy(waypoints = SyntheticRoute.route.waypoints.map { it.copy(id = "same") })
    }
}
