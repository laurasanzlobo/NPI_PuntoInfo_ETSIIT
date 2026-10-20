package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test

/** Tiempos monotónicos simulados, no medidas del receptor GPS físico. */
class ActiveLocationTest {
    private class StreamingSource : DeviceLocationSource {
        val fixes = Channel<DeviceCoordinates?>(Channel.UNLIMITED)
        val intervals = mutableListOf<Long>()
        var subscribed = false
        override fun permission() = LocationPermission.PRECISE
        override fun isLocationEnabled() = true
        override suspend fun currentLocation(permission: LocationPermission) = coordinates(1_000)
        override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
            intervals += intervalMillis
            subscribed = true
            try { for (fix in fixes) emit(fix) } finally { subscribed = false }
        }
    }

    private fun navigator(source: StreamingSource, calls: () -> Unit = {}) = OutdoorNavigator(
        GpsLocationReader(source), object : WalkingRouteSource {
            override suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult {
                calls()
                return WalkingRouteResult.Success(WalkingRoute(listOf(origin, destination), 10.0, 10.0, emptyList()))
            }
        }, "etsiit")

    @Test fun simulatedStreamConfirmsAndRequestsPromptAfterTwoSecondsWithoutWaitingForPolling() = runBlocking {
        val source = StreamingSource()
        var routeCalls = 0
        val navigator = navigator(source) { routeCalls++ }
        navigator.requestRoute(this)
        yield()
        val indoor = NavigationJourney.Indoor(IndoorTarget("Aula 0.1", "PB", "aula"), "entrada")
        var trip = NavigationTrip.start(NavigationJourney.Combined(OutdoorDestinations.find("etsiit")!!, indoor))
        val detector = ArrivalDetector()
        var promptAt: Long? = null
        val tracking = launch {
            navigator.trackActivePosition(ActiveLocationPolicy.approachingIntervalMillis) { location ->
                val fix = (location as? GpsLocationState.Available)?.coordinates!!
                val time = fix.elapsedRealtimeMillis!!
                if (detector.observe(10.0, fix.accuracyMeters.toDouble(), time, time + 50)) {
                    trip = trip.onArrival()
                    promptAt = time + 50
                }
            }
        }
        yield()
        source.fixes.send(coordinates(100_000)); yield()
        assertEquals(ArrivalPresentation.WAITING, trip.presentation)
        source.fixes.send(coordinates(102_000)); yield()
        assertEquals(ArrivalPresentation.PROMPT, trip.presentation)
        assertEquals(2_050L, promptAt!! - detector.firstValidFixMillis!!)
        assertEquals(listOf(2_000L), source.intervals)
        assertEquals(1, routeCalls)
        assertTrue(navigator.state.value.route is WalkingRouteResult.Success)
        tracking.cancelAndJoin()
        assertFalse(source.subscribed)
    }

    @Test fun changingFrequencyAndStoppingCancelsOldSubscriptionWithoutRecalculatingRoute() = runBlocking {
        val source = StreamingSource()
        var routeCalls = 0
        val navigator = navigator(source) { routeCalls++ }
        navigator.requestRoute(this); yield()
        val cruising = launch { navigator.trackActivePosition(12_000) {} }
        yield()
        assertTrue(source.subscribed)
        cruising.cancelAndJoin()
        assertFalse(source.subscribed)
        val approaching = launch { navigator.trackActivePosition(2_000) {} }
        yield()
        navigator.onStop()
        approaching.cancelAndJoin()
        assertFalse(source.subscribed)
        assertEquals(listOf(12_000L, 2_000L), source.intervals)
        assertEquals(1, routeCalls)
        assertNull(navigator.state.value.route)
        navigator.trackActivePosition(2_000) { fail("No debe suscribirse sin ruta activa") }
        assertFalse(source.subscribed)
    }

    @Test fun streamRejectsInvalidCoordinatesBeforeArrivalProcessing() = runBlocking {
        val source = StreamingSource()
        val reader = GpsLocationReader(source)
        val states = mutableListOf<GpsLocationState>()
        val tracking = launch { reader.updates(2_000).collect { states += it } }
        yield()
        source.fixes.send(DeviceCoordinates(Double.NaN, 0.0, 5f, 1_000)); yield()
        source.fixes.send(null); yield()
        source.fixes.send(coordinates(3_000)); yield()
        assertEquals(listOf(GpsLocationState.Unavailable, GpsLocationState.Unavailable), states.take(2))
        assertTrue(states.last() is GpsLocationState.Available)
        tracking.cancelAndJoin()
        assertFalse(source.subscribed)
    }

    companion object {
        private fun coordinates(time: Long) = DeviceCoordinates(
            CampusLocationManager.ETSIIT_LATITUDE, CampusLocationManager.ETSIIT_LONGITUDE, 5f, time)
    }
}
