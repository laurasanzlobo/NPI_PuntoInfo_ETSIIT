package com.example.npi_puntoinfo_etsiit.outdoor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

data class RouteHttpResponse(val status: Int, val body: String)

/** Inyectable: las pruebas no envían posiciones ni necesitan una clave real. */
fun interface RouteTransport {
    suspend fun post(apiKey: String, body: String): RouteHttpResponse
}

class HttpsRouteTransport : RouteTransport {
    override suspend fun post(apiKey: String, body: String): RouteHttpResponse = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val connection = URL("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
            .openConnection() as HttpsURLConnection
        try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false // No reenviar la clave a otro host.
                useCaches = false
                doOutput = true
                setRequestProperty("Authorization", apiKey)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/geo+json, application/json")
                setRequestProperty("User-Agent", "NPI-ETSIIT-Outdoor/1.0")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            coroutineContext.ensureActive()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            // Limita memoria y no persiste ni registra coordenadas, respuestas o credenciales.
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8192)
                val result = StringBuilder()
                while (true) {
                    coroutineContext.ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > 2_000_000) throw IOException("Respuesta demasiado grande")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }.orEmpty()
            RouteHttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}

fun interface WalkingRouteSource {
    suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult
}

class OpenRouteService(private val apiKey: String, private val transport: RouteTransport = HttpsRouteTransport()) : WalkingRouteSource {
    override suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult {
        if (!origin.isValid || !destination.isValid) return WalkingRouteResult.Failure(RouteError.INVALID_POSITION)
        if (apiKey.isBlank()) return WalkingRouteResult.Failure(RouteError.MISSING_KEY)
        return try {
            val response = transport.post(apiKey, requestBody(origin, destination))
            coroutineContext.ensureActive()
            decode(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            WalkingRouteResult.Failure(RouteError.OFFLINE)
        } catch (_: Exception) {
            WalkingRouteResult.Failure(RouteError.SERVICE)
        }
    }

    companion object {
        fun requestBody(origin: GeoPoint, destination: GeoPoint): String = JsonObject().apply {
            add("coordinates", JsonArray().apply {
                listOf(origin, destination).forEach { point ->
                    add(JsonArray().apply { add(point.longitude); add(point.latitude) })
                }
            })
            addProperty("language", "es")
            addProperty("instructions", true)
            addProperty("instructions_format", "text")
            addProperty("units", "m")
            // No conducir al usuario a un camino a kilómetros de las coordenadas del catálogo.
            add("radiuses", JsonArray().apply { add(100); add(100) })
        }.toString()

        fun decode(response: RouteHttpResponse): WalkingRouteResult {
            fun failure(error: RouteError) = WalkingRouteResult.Failure(error)
            when (response.status) {
                401, 403 -> return failure(RouteError.UNAUTHORIZED)
                429 -> return failure(RouteError.RATE_LIMIT)
                in 500..599 -> return failure(RouteError.SERVICE)
            }
            return try {
                val root = JsonParser.parseString(response.body).asJsonObject
                val error = root.getAsJsonObject("error")
                if (error != null) {
                    return failure(if (error.get("code")?.asInt in listOf(2009, 2010))
                        RouteError.NO_ROUTE else RouteError.SERVICE)
                }
                if (response.status !in 200..299) return failure(RouteError.SERVICE)
                require(root.get("type").asString == "FeatureCollection")
                val features = root.getAsJsonArray("features")
                if (features.isEmpty) return failure(RouteError.NO_ROUTE)
                val feature = features[0].asJsonObject
                val geometry = feature.getAsJsonObject("geometry")
                require(geometry.get("type").asString == "LineString")
                val points = geometry.getAsJsonArray("coordinates").map { coordinate ->
                    val values = coordinate.asJsonArray
                    require(values.size() >= 2)
                    GeoPoint(values[1].asDouble, values[0].asDouble).also { require(it.isValid) }
                }
                require(points.size >= 2)
                val properties = feature.getAsJsonObject("properties")
                val summary = properties.getAsJsonObject("summary")
                val distance = summary.get("distance").asDouble
                val duration = summary.get("duration")?.takeUnless { it.isJsonNull }?.asDouble
                require(distance.isFinite() && distance >= 0)
                require(duration == null || (duration.isFinite() && duration >= 0))
                val steps = properties.getAsJsonArray("segments")?.flatMap { segment ->
                    segment.asJsonObject.getAsJsonArray("steps")?.map { item ->
                        val step = item.asJsonObject
                        val meters = step.get("distance").asDouble
                        require(meters.isFinite() && meters >= 0)
                        WalkingStep(step.get("instruction").asString, meters,
                            Maneuver.fromOrsType(step.get("type")?.takeUnless { it.isJsonNull }?.asInt))
                    }.orEmpty()
                }.orEmpty()
                WalkingRouteResult.Success(WalkingRoute(points, distance, duration, steps))
            } catch (_: Exception) {
                failure(RouteError.INVALID_RESPONSE)
            }
        }
    }
}
