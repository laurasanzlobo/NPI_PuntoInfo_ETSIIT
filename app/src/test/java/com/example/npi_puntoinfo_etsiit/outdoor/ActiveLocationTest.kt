package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test

class ActiveLocationTest {
    @Test fun simulatedPositionsReachTrackerWithoutAnyRouteRequest() = runBlocking {
        val fixes = Channel<DeviceCoordinates?>(Channel.UNLIMITED)
        var subscribed = false
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = error("Solo streaming")
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                assertEquals(2_000L, intervalMillis)
                subscribed = true
                try { for (fix in fixes) emit(fix) } finally { subscribed = false }
            }
        }
        var now = 1_000L
        val navigator = OutdoorNavigator(FixedRouteTracker(SyntheticRoute.route), GpsLocationReader(source))
        navigator.start()
        val job = launch { navigator.track { now } }
        yield()
        for (x in 0..50 step 5) {
            fixes.send(SyntheticRoute.fix(x.toDouble(), 0.0, now)); yield(); now += 2_000
        }
        repeat(3) { fixes.send(SyntheticRoute.fix(50.0, 0.0, now)); yield(); now += 2_000 }
        assertEquals(0, navigator.state.value.stepIndex)
        assertEquals(1, navigator.state.value.suggestedStepIndex)
        navigator.confirmSuggestedStep()
        assertEquals(1, navigator.state.value.stepIndex)
        assertEquals(ProgressSource.GPS, navigator.state.value.source)
        job.cancelAndJoin()
        assertFalse(subscribed)
    }
}
