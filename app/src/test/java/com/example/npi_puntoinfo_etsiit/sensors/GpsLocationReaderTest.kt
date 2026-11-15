package com.example.npi_puntoinfo_etsiit.sensors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GpsLocationReaderTest {
    private class FakeSource : DeviceLocationSource {
        var access = LocationPermission.PRECISE
        var enabled = true
        var coordinates: DeviceCoordinates? = DeviceCoordinates(37.19708, -3.62446, 8f)
        var calls = 0
        var requestedPermission: LocationPermission? = null
        var onRequest: () -> Unit = {}

        override fun permission() = access
        override fun isLocationEnabled() = enabled
        override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? {
            calls++
            requestedPermission = permission
            onRequest()
            return coordinates
        }
    }

    @Test
    fun deniedPermissionNeverRequestsLocation() = runBlocking {
        val source = FakeSource().apply { access = LocationPermission.DENIED }
        val reader = GpsLocationReader(source)
        assertEquals(GpsLocationState.PermissionDenied, reader.accessState())
        assertEquals(GpsLocationState.PermissionDenied, reader.read())
        assertEquals(0, source.calls)
    }

    @Test
    fun disabledLocationNeverRequestsLocation() = runBlocking {
        val source = FakeSource().apply { enabled = false }
        val reader = GpsLocationReader(source)
        assertEquals(GpsLocationState.LocationDisabled, reader.accessState())
        assertEquals(GpsLocationState.LocationDisabled, reader.read())
        assertEquals(0, source.calls)
    }

    @Test
    fun unavailableFixDoesNotReturnCoordinatesOrDistances() = runBlocking {
        val source = FakeSource().apply { coordinates = null }
        assertEquals(GpsLocationState.Unavailable, GpsLocationReader(source).read())
        assertEquals(1, source.calls)
    }

    @Test
    fun newUnavailableRequestDoesNotReusePreviousPosition() = runBlocking {
        val source = FakeSource()
        val reader = GpsLocationReader(source)
        assertTrue(reader.read() is GpsLocationState.Available)
        source.coordinates = null
        assertEquals(GpsLocationState.Unavailable, reader.read())
        assertEquals(2, source.calls)
    }

    @Test
    fun preciseAndApproximatePermissionsBothAcceptValidCoordinates() = runBlocking {
        listOf(LocationPermission.PRECISE, LocationPermission.APPROXIMATE).forEach { permission ->
            val source = FakeSource().apply {
                access = permission
                coordinates = DeviceCoordinates(37.19708, -3.62446,
                    if (permission == LocationPermission.PRECISE) 8f else 1_500f)
            }
            val result = GpsLocationReader(source).read() as GpsLocationState.Available
            assertEquals(permission, result.permission)
            assertEquals(permission, source.requestedPermission)
            assertEquals(source.coordinates, result.coordinates)
            assertEquals("aynadamar", OutdoorDistances.nearestDiningHall(
                result.coordinates.latitude, result.coordinates.longitude
            )?.hall?.id)
            assertEquals(1, source.calls)
        }
    }

    @Test
    fun invalidCoordinatesOrAccuracyAreUnavailable() = runBlocking {
        listOf(
            DeviceCoordinates(Double.NaN, -3.6, 5f),
            DeviceCoordinates(91.0, -3.6, 5f),
            DeviceCoordinates(37.2, -181.0, 5f),
            DeviceCoordinates(37.2, -3.6, -1f),
            DeviceCoordinates(37.2, -3.6, Float.NaN),
            DeviceCoordinates(37.2, -3.6, Float.POSITIVE_INFINITY)
        ).forEach { invalid ->
            val source = FakeSource().apply { coordinates = invalid }
            assertEquals(GpsLocationState.Unavailable, GpsLocationReader(source).read())
        }
    }

    @Test
    fun permissionRevokedDuringRequestDoesNotExposePosition() = runBlocking {
        val source = FakeSource()
        source.onRequest = { source.access = LocationPermission.DENIED }
        assertEquals(GpsLocationState.PermissionDenied, GpsLocationReader(source).read())
    }

    @Test
    fun locationDisabledDuringRequestDoesNotExposePosition() = runBlocking {
        val source = FakeSource()
        source.onRequest = { source.enabled = false }
        assertEquals(GpsLocationState.LocationDisabled, GpsLocationReader(source).read())
    }

    @Test
    fun precisionDowngradeDuringRequestDiscardsPrecisePosition() = runBlocking {
        val source = FakeSource()
        source.onRequest = { source.access = LocationPermission.APPROXIMATE }
        assertEquals(GpsLocationState.Unavailable, GpsLocationReader(source).read())
    }

    @Test
    fun securityExceptionIsTreatedAsPermissionDenied() = runBlocking {
        val source = FakeSource().apply { onRequest = { throw SecurityException() } }
        assertEquals(GpsLocationState.PermissionDenied, GpsLocationReader(source).read())
    }

    @Test
    fun serviceFailureIsTreatedAsUnavailable() = runBlocking {
        val source = FakeSource().apply { onRequest = { throw IllegalStateException() } }
        assertEquals(GpsLocationState.Unavailable, GpsLocationReader(source).read())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() = runBlocking<Unit> {
        val source = FakeSource().apply { onRequest = { throw CancellationException() } }
        GpsLocationReader(source).read()
    }
}
