package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Locale

class OutdoorRoutingTest {
    private val origin = OutdoorDestinations.find("etsiit")!!.point
    private val destination = OutdoorDestinations.forDiningHall("fuentenueva")!!.point

    // Respuesta sintética de contrato; NO se usa como ruta real en la aplicación.
    private val fixture = """{
      "type":"FeatureCollection", "features":[{
        "type":"Feature", "geometry":{"type":"LineString","coordinates":[[-3.62446,37.19708],[-3.6087,37.1812]]},
        "properties":{"summary":{"distance":2345.6,"duration":1801},
          "segments":[{"steps":[{"instruction":"Continúa a pie","distance":2345.6}]}]}
      }]
    }""".trimIndent()

    private fun decode(body: String = fixture, status: Int = 200) =
        OpenRouteService.decode(RouteHttpResponse(status, body))

    @Test fun catalogReusesEveryDiningCoordinateAndId() {
        CampusLocationManager.ugrDiningHalls.forEach { hall ->
            val outdoor = OutdoorDestinations.forDiningHall(hall.id)!!
            assertEquals(hall.name, outdoor.name)
            assertEquals(hall.latitude, outdoor.point.latitude, 0.0)
            assertEquals(hall.longitude, outdoor.point.longitude, 0.0)
        }
        assertEquals(OutdoorDestinations.all.size, OutdoorDestinations.all.map { it.id }.toSet().size)
        assertTrue(OutdoorDestinations.all.all { it.point.isValid })
    }

    @Test fun catalogReusesEtsiitCoordinates() {
        assertEquals(CampusLocationManager.ETSIIT_LATITUDE, origin.latitude, 0.0)
        assertEquals(CampusLocationManager.ETSIIT_LONGITUDE, origin.longitude, 0.0)
    }

    @Test fun unknownDestinationNeverFallsBackToDifferentBuilding() {
        assertNull(OutdoorDestinations.find("unknown"))
        assertNull(OutdoorDestinations.find(null))
        assertNull(OutdoorDestinations.forDiningHall("unknown"))
    }

    @Test fun outdoorRouteNeverUsesIndoorNamespace() {
        OutdoorDestinations.all.forEach {
            assertEquals("outdoor?destinationId=${it.id}", OutdoorNavigationRoute.create(it.id))
            assertFalse(OutdoorNavigationRoute.create(it.id).startsWith("navigation"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun routeRejectsQueryInjection() { OutdoorNavigationRoute.create("id&destination=Aula") }

    @Test fun requestUsesLongitudeFirstWalkingOptionsAndMetricUnits() {
        val body = JsonParser.parseString(OpenRouteService.requestBody(origin, destination)).asJsonObject
        val coordinates = body.getAsJsonArray("coordinates")
        assertEquals(origin.longitude, coordinates[0].asJsonArray[0].asDouble, 0.0)
        assertEquals(origin.latitude, coordinates[0].asJsonArray[1].asDouble, 0.0)
        assertEquals(destination.longitude, coordinates[1].asJsonArray[0].asDouble, 0.0)
        assertEquals("m", body.get("units").asString)
        assertEquals("es", body.get("language").asString)
        assertTrue(body.get("instructions").asBoolean)
        assertEquals(100, body.getAsJsonArray("radiuses")[1].asInt)
    }

    @Test fun validProviderResponsePreservesGeometryDistanceDurationAndInstructions() {
        val route = (decode() as WalkingRouteResult.Success).route
        assertEquals(origin, route.points.first())
        assertEquals(destination, route.points.last())
        assertEquals(2345.6, route.distanceMeters, 0.0)
        assertEquals(1801.0, route.durationSeconds!!, 0.0)
        assertEquals("Continúa a pie", route.steps.single().instruction)
    }

    @Test fun durationCanBeAbsentWithoutInventingWalkingTime() {
        val route = (decode(fixture.replace(",\"duration\":1801", "")) as WalkingRouteResult.Success).route
        assertNull(route.durationSeconds)
    }

    @Test fun emptyFeaturesAreNoRouteNotStraightLine() {
        assertEquals(WalkingRouteResult.Failure(RouteError.NO_ROUTE),
            decode("""{"type":"FeatureCollection","features":[]}"""))
    }

    @Test fun noRouteAndUnsnappablePointAreExplicit() {
        listOf(2009, 2010).forEach { code ->
            assertEquals(WalkingRouteResult.Failure(RouteError.NO_ROUTE),
                decode("""{"error":{"code":$code}}""", 404))
        }
    }

    @Test fun incompatibleParametersAreNotReportedAsUnreachableDestination() {
        assertEquals(WalkingRouteResult.Failure(RouteError.SERVICE),
            decode("""{"error":{"code":2011}}""", 400))
    }

    @Test fun authenticationQuotaAndServerErrorsAreDistinct() {
        mapOf(401 to RouteError.UNAUTHORIZED, 403 to RouteError.UNAUTHORIZED,
            429 to RouteError.RATE_LIMIT, 500 to RouteError.SERVICE, 503 to RouteError.SERVICE).forEach { (status, error) ->
            assertEquals(WalkingRouteResult.Failure(error), decode("not-json", status))
        }
    }

    @Test fun malformedOrMissingGeometryNeverProducesRoute() {
        listOf("not-json", "{}", fixture.replace("LineString", "Point"),
            fixture.replace("[-3.6087,37.1812]", "[-3.6087,91]"),
            fixture.replace("[[-3.62446,37.19708],[-3.6087,37.1812]]", "[]"),
            fixture.replace("2345.6", "-1"), fixture.replace("1801", "-1"),
            fixture.replace("2345.6", "1e999")).forEach { invalid ->
            assertEquals(WalkingRouteResult.Failure(RouteError.INVALID_RESPONSE), decode(invalid))
        }
    }

    @Test fun missingKeyAndInvalidCoordinatesNeverUseNetwork() = runBlocking {
        var calls = 0
        val transport = RouteTransport { _, _ -> calls++; RouteHttpResponse(200, fixture) }
        assertEquals(WalkingRouteResult.Failure(RouteError.MISSING_KEY), OpenRouteService("", transport).route(origin, destination))
        assertEquals(WalkingRouteResult.Failure(RouteError.INVALID_POSITION),
            OpenRouteService("test-only", transport).route(GeoPoint(Double.NaN, 0.0), destination))
        assertEquals(0, calls)
    }

    @Test fun validRequestReachesInjectedTransportExactlyOnce() = runBlocking {
        var calls = 0
        val service = OpenRouteService("test-only", RouteTransport { key, body ->
            calls++
            assertEquals("test-only", key)
            assertEquals(OpenRouteService.requestBody(origin, destination), body)
            RouteHttpResponse(200, fixture)
        })
        assertTrue(service.route(origin, destination) is WalkingRouteResult.Success)
        assertEquals(1, calls)
    }

    @Test fun networkErrorIsRecoverable() = runBlocking {
        val service = OpenRouteService("test-only", RouteTransport { _, _ -> throw IOException() })
        assertEquals(WalkingRouteResult.Failure(RouteError.OFFLINE), service.route(origin, destination))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagated() = runBlocking<Unit> {
        OpenRouteService("test-only", RouteTransport { _, _ -> throw CancellationException() }).route(origin, destination)
    }

    @Test fun distancesAndDurationsUseCorrectUnits() {
        assertEquals("0 m", formatOutdoorDistance(0.0))
        assertEquals("999 m", formatOutdoorDistance(999.0))
        assertEquals("1.00 km", formatOutdoorDistance(1000.0, Locale.US))
        assertEquals("2,35 km", formatOutdoorDistance(2345.6, Locale.forLanguageTag("es-ES")))
        assertEquals("1 min", formatWalkingDuration(1.0))
        assertEquals("31 min", formatWalkingDuration(1801.0))
        assertEquals("1 h 1 min", formatWalkingDuration(3601.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDistanceCannotBeFormattedAsRealResult() { formatOutdoorDistance(Double.NaN) }
}
