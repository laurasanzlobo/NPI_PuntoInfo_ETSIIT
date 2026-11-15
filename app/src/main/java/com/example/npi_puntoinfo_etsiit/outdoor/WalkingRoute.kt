package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WalkingStep(
    val instruction: String,
    val distanceMeters: Double,
    val maneuver: Maneuver = Maneuver.UNKNOWN,
    val photo: String? = null,
    val heading: Int? = null,
    val ejeXCamino: Double = 0.5,
    val nodeName: String? = null,
    val nodeId: String? = null,
    val point: GeoPoint? = null
)

enum class Maneuver {
    LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, STRAIGHT,
    ROUNDABOUT, EXIT_ROUNDABOUT, U_TURN, ARRIVAL, DEPART, KEEP_LEFT, KEEP_RIGHT, UNKNOWN
}

data class WalkingRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val steps: List<WalkingStep>
) {
    /** Índice del nodo más cercano, sin exigir estar dentro del radio de avance de paso. */
    fun nearestStepIndex(position: GeoPoint?): Int? {
        if (position == null || !position.isValid) return null
        var nearestIndex: Int? = null
        var nearestDistance = Double.POSITIVE_INFINITY
        steps.forEachIndexed { index, step ->
            val point = step.point ?: return@forEachIndexed
            if (!point.isValid) return@forEachIndexed
            val distance = OutdoorDistances.distanceMeters(
                position.latitude, position.longitude, point.latitude, point.longitude
            )
            if (distance < nearestDistance) {
                nearestIndex = index
                nearestDistance = distance
            }
        }
        return nearestIndex
    }
}

enum class RouteError(val message: String) {
    INVALID_POSITION("Origen o destino no válido."),
    NO_ROUTE("No hay ruta exterior calibrada disponible para este destino."),
    SERVICE("No se ha podido cargar la ruta exterior.")
}

fun interface WalkingRouteSource {
    suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult
}

sealed interface WalkingRouteResult {
    data class Success(val route: WalkingRoute) : WalkingRouteResult
    data class Failure(val error: RouteError) : WalkingRouteResult
}

fun formatOutdoorDistance(meters: Double, locale: Locale = Locale.getDefault()): String {
    require(meters.isFinite() && meters >= 0)
    return if (meters < 1000) "${meters.roundToInt()} m"
    else String.format(locale, "%.2f km", meters / 1000)
}

fun formatWalkingDuration(seconds: Double): String {
    require(seconds.isFinite() && seconds >= 0)
    val minutes = ceil(seconds / 60).toInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
