package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorNavigatorTest {
    private class LocationSource : DeviceLocationSource {
        var permission = LocationPermission.PRECISE
        var enabled = true
        var coordinates: DeviceCoordinates? = DeviceCoordinates(
            CampusLocationManager.ETSIIT_LATITUDE, CampusLocationManager.ETSIIT_LONGITUDE, 8f)
        var reads = 0
        override fun permission() = permission
        override fun isLocationEnabled() = enabled
        override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? {
            reads++
            return coordinates
        }
    }

    private class Routes : WalkingRouteSource {
        var calls = 0
        var requestedDestination: GeoPoint? = null
        var result: WalkingRouteResult = WalkingRouteResult.Failure(RouteError.NO_ROUTE)
        var onRequest: suspend () -> Unit = {}
        override suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult {
            calls++
            requestedDestination = destination
            onRequest()
            return result
        }
    }

    @Test fun openingScreenAndSelectingDestinationNeverSendLocation() {
        val source = LocationSource()
        val routes = Routes()
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "dining-aynadamar")
        navigator.onStart()
        navigator.selectDestination("etsiit")
        assertEquals("etsiit", navigator.state.value.destination?.id)
        assertEquals(0, source.reads)
        assertEquals(0, routes.calls)
    }

    @Test fun locationOnlyDoesNotContactRoutingProvider() = runBlocking {
        val source = LocationSource()
        val routes = Routes()
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "dining-aynadamar")
        navigator.refreshLocation(this)
        yield()
        assertTrue(navigator.state.value.location is GpsLocationState.Available)
        assertEquals(0, routes.calls)
    }

    @Test fun invalidDestinationDoesNotStartRequests() = runBlocking {
        val source = LocationSource()
        val routes = Routes()
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "unknown")
        navigator.requestRoute(this)
        yield()
        assertNull(navigator.state.value.destination)
        assertEquals(0, source.reads)
        assertEquals(0, routes.calls)
    }

    @Test fun deniedDisabledAndUnavailableLocationNeverReachRoutingService() = runBlocking {
        listOf<LocationSource.() -> Unit>(
            { permission = LocationPermission.DENIED }, { enabled = false }, { coordinates = null }
        ).forEach { configure ->
            val source = LocationSource().apply(configure)
            val routes = Routes()
            val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "dining-aynadamar")
            navigator.requestRoute(this)
            yield()
            assertEquals(0, routes.calls)
            assertFalse(navigator.state.value.routing)
            assertNull(navigator.state.value.route)
        }
    }

    @Test fun explicitRequestsUseSelectedDestinationAndFreshGps() = runBlocking {
        val source = LocationSource()
        val routes = Routes()
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "dining-aynadamar")
        navigator.requestRoute(this)
        yield()
        navigator.selectDestination("dining-pts")
        assertNull(navigator.state.value.route)
        navigator.requestRoute(this)
        yield()
        assertEquals(2, source.reads)
        assertEquals(2, routes.calls)
        assertEquals(OutdoorDestinations.forDiningHall("pts")!!.point, routes.requestedDestination)
        assertEquals(WalkingRouteResult.Failure(RouteError.NO_ROUTE), navigator.state.value.route)
    }

    @Test fun doubleClickDoesNotDuplicatePendingRequest() = runBlocking {
        val routes = Routes().apply { onRequest = { awaitCancellation() } }
        val navigator = OutdoorNavigator(GpsLocationReader(LocationSource()), routes, "etsiit")
        navigator.requestRoute(this)
        yield()
        navigator.requestRoute(this)
        yield()
        assertEquals(1, routes.calls)
        navigator.onStop()
        yield()
        assertFalse(navigator.state.value.routing)
    }

    @Test fun destinationChangeCancelsOldRouteAndNeverPublishesIt() = runBlocking {
        val routes = Routes().apply { onRequest = { awaitCancellation() } }
        val navigator = OutdoorNavigator(GpsLocationReader(LocationSource()), routes, "etsiit")
        navigator.requestRoute(this)
        yield()
        navigator.selectDestination("dining-cartuja")
        yield()
        assertNull(navigator.state.value.route)
        assertFalse(navigator.state.value.routing)
        assertEquals("dining-cartuja", navigator.state.value.destination?.id)
    }

    @Test fun leavingScreenClearsPositionRouteAndPendingRequests() = runBlocking {
        val navigator = OutdoorNavigator(GpsLocationReader(LocationSource()), Routes(), "etsiit")
        navigator.requestRoute(this)
        yield()
        assertTrue(navigator.state.value.location is GpsLocationState.Available)
        assertNotNull(navigator.state.value.route)
        navigator.onStop()
        assertEquals(GpsLocationState.Idle, navigator.state.value.location)
        assertNull(navigator.state.value.route)
    }

    @Test fun permissionRevocationOnReturnRemovesPosition() = runBlocking {
        val source = LocationSource()
        val navigator = OutdoorNavigator(GpsLocationReader(source), Routes(), "etsiit")
        navigator.refreshLocation(this)
        yield()
        source.permission = LocationPermission.DENIED
        navigator.onStart()
        assertEquals(GpsLocationState.PermissionDenied, navigator.state.value.location)
        assertNull(navigator.state.value.route)
    }

    @Test fun activePositionUpdatesPreserveRouteAndNeverContactServiceAgain() = runBlocking {
        val source = LocationSource()
        val route = WalkingRoute(listOf(GeoPoint(37.19, -3.62), GeoPoint(37.20, -3.63)), 200.0, 150.0, emptyList())
        val routes = Routes().apply { result = WalkingRouteResult.Success(route) }
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "etsiit")
        navigator.requestRoute(this)
        yield()
        source.coordinates = DeviceCoordinates(37.1971, -3.6244, 5f)
        navigator.updateActivePosition()
        assertEquals(2, source.reads)
        assertEquals(1, routes.calls)
        assertEquals(route, (navigator.state.value.route as WalkingRouteResult.Success).route)
        assertEquals(source.coordinates, (navigator.state.value.location as GpsLocationState.Available).coordinates)
        navigator.onStop()
        navigator.updateActivePosition()
        assertEquals(2, source.reads)
    }

    @Test fun lostPermissionDuringNavigationDoesNotInventArrivalOrDiscardRoute() = runBlocking {
        val source = LocationSource()
        val routes = Routes().apply {
            result = WalkingRouteResult.Success(WalkingRoute(emptyList(), 0.0, null, emptyList()))
        }
        val navigator = OutdoorNavigator(GpsLocationReader(source), routes, "dining-pts")
        navigator.requestRoute(this)
        yield()
        source.permission = LocationPermission.DENIED
        navigator.updateActivePosition()
        assertEquals(GpsLocationState.PermissionDenied, navigator.state.value.location)
        assertEquals("dining-pts", navigator.state.value.destination!!.id)
        assertEquals(1, routes.calls)
    }
}
