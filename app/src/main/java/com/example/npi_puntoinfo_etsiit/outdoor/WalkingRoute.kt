package com.example.npi_puntoinfo_etsiit.outdoor

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WalkingStep(val instruction: String, val distanceMeters: Double, val maneuver: Maneuver = Maneuver.UNKNOWN)

enum class Maneuver {
    LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, STRAIGHT,
    ROUNDABOUT, EXIT_ROUNDABOUT, U_TURN, ARRIVAL, DEPART, KEEP_LEFT, KEEP_RIGHT, UNKNOWN;

    companion object {
        fun fromOrsType(type: Int?): Maneuver = when (type) {
            0 -> LEFT; 1 -> RIGHT; 2 -> SHARP_LEFT; 3 -> SHARP_RIGHT
            4 -> SLIGHT_LEFT; 5 -> SLIGHT_RIGHT; 6 -> STRAIGHT; 7 -> ROUNDABOUT
            8 -> EXIT_ROUNDABOUT; 9 -> U_TURN; 10 -> ARRIVAL; 11 -> DEPART
            12 -> KEEP_LEFT; 13 -> KEEP_RIGHT; else -> UNKNOWN
        }
    }
}

data class WalkingRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val steps: List<WalkingStep>
)

enum class RouteError(val message: String) {
    MISSING_KEY("Rutas sin configurar. Añade ORS_API_KEY a local.properties y recompila (consulta docs/T4_EXTERIOR.md)."),
    INVALID_POSITION("Origen o destino no válido."),
    OFFLINE("Sin conexión o fallo de red. Comprueba Internet y vuelve a solicitar la ruta."),
    UNAUTHORIZED("La clave de openrouteservice no es válida o no tiene acceso a Directions."),
    RATE_LIMIT("Se ha alcanzado el límite de openrouteservice. Espera antes de reintentar."),
    NO_ROUTE("No hay ruta peatonal disponible o no se encuentra un camino próximo al origen/destino."),
    SERVICE("El servicio de rutas no está disponible. Inténtalo más tarde."),
    INVALID_RESPONSE("El proveedor ha devuelto una ruta no válida. No se dibujará un recorrido inventado.")
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
