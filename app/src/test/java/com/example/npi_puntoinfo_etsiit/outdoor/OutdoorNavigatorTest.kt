package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class OutdoorNavigatorTest {
    @Test fun missingFirstFixShowsAnExplanationAndALateFixStillSelectsItsNode() = runBlocking {
        val fixes = Channel<DeviceCoordinates>(Channel.UNLIMITED)
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                for (fix in fixes) emit(fix)
            }
        }
        val navigator = OutdoorNavigator(FixedRouteTracker(SyntheticRoute.route,
            TrackingPolicy(firstFixTimeoutMillis = 30)), GpsLocationReader(source))
        navigator.start()
        val job = launch { navigator.track { 1_000L } }
        withTimeout(2_000) { navigator.state.first { it.gpsMessage.contains("No se recibe ubicación") } }
        assertTrue(navigator.state.value.gpsProblem)
        fixes.send(SyntheticRoute.fix(50.0, 0.0, 1_000, 25f))
        withTimeout(2_000) { navigator.state.first { it.positionConfirmed } }
        assertEquals(1, navigator.state.value.stepIndex)
        assertFalse(navigator.state.value.gpsProblem)
        job.cancelAndJoin()
    }

    @Test fun manualDemonstrationNeedsNeitherGpsNorNetwork() {
        var reads = 0
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.DENIED
            override fun isLocationEnabled() = false
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? { reads++; return null }
        }
        val navigator = OutdoorNavigator(FixedRouteTracker(EtsiitAuxiliaryRoute.route), GpsLocationReader(source))
        navigator.start(); repeat(EtsiitAuxiliaryRoute.route.waypoints.lastIndex) { navigator.next() }
        assertTrue(navigator.state.value.arrived)
        assertEquals(ProgressSource.MANUAL, navigator.state.value.source)
        repeat(EtsiitAuxiliaryRoute.route.waypoints.lastIndex) { navigator.previous() }
        assertEquals(0, navigator.state.value.stepIndex)
        navigator.restart()
        assertEquals(GuidanceState(), navigator.state.value)
        navigator.start(); navigator.finish()
        assertFalse(navigator.state.value.started)
        assertEquals(0, reads)
    }

    @Test fun deniedPermissionDoesNotPreventManualProgress() = runBlocking {
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.DENIED
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = error("No debe leer")
        }
        val navigator = OutdoorNavigator(FixedRouteTracker(SyntheticRoute.route), GpsLocationReader(source))
        navigator.start()
        val job = launch { navigator.track { 1_000L } }
        yield()
        assertTrue(navigator.state.value.gpsMessage.contains("Sin permiso"))
        navigator.next()
        assertEquals(1, navigator.state.value.stepIndex)
        job.cancelAndJoin()
    }

    @Test fun cancellationRemovesSubscriptionAndGpsEvidenceButKeepsManualProgress() = runBlocking {
        var subscribed = false
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow<DeviceCoordinates?> {
                subscribed = true
                try { emit(SyntheticRoute.fix(0.0, 0.0, 1_000)); awaitCancellation() }
                finally { subscribed = false }
            }
        }
        val navigator = OutdoorNavigator(FixedRouteTracker(SyntheticRoute.route), GpsLocationReader(source))
        navigator.start()
        val job = launch { navigator.track { 1_000L } }
        yield()
        assertTrue(subscribed)
        job.cancelAndJoin()
        assertFalse(subscribed)
        assertTrue(navigator.state.value.started)
        assertNull(navigator.state.value.segment)
        assertNull(navigator.state.value.remainingMeters)
    }

    @Test fun silentProviderExpiresTheLastPositionWithoutAdvancing() = runBlocking {
        val source = object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow<DeviceCoordinates?> {
                emit(SyntheticRoute.fix(0.0, 0.0, 1_000)); awaitCancellation()
            }
        }
        val navigator = OutdoorNavigator(FixedRouteTracker(SyntheticRoute.route,
            TrackingPolicy(maxFixAgeMillis = 50)), GpsLocationReader(source))
        navigator.start()
        val job = launch { navigator.track { 1_000L } }
        withTimeout(2_000) { navigator.state.first { it.segment != null } }
        withTimeout(2_000) { navigator.state.first { it.gpsMessage.contains("Sin posición GPS reciente") } }
        assertEquals(0, navigator.state.value.stepIndex)
        assertNull(navigator.state.value.remainingMeters)
        job.cancelAndJoin()
    }
}
