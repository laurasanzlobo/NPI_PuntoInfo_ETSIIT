package com.example.npi_puntoinfo_etsiit.sensors

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DiningHallDistance(val hall: UgrDiningHall, val meters: Double)

/** Distancias geográficas (Haversine), no rutas ni tiempos de marcha. */
object OutdoorDistances {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(validCoordinates(lat1, lon1) && validCoordinates(lat2, lon2))
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val a = (sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(deltaLon / 2) * sin(deltaLon / 2)).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Sin posición válida no se inventa un comedor más cercano. */
    fun distancesFrom(latitude: Double?, longitude: Double?): List<DiningHallDistance> {
        if (latitude == null || longitude == null || !validCoordinates(latitude, longitude)) {
            return emptyList()
        }
        return CampusLocationManager.ugrDiningHalls.map { hall ->
            DiningHallDistance(hall, distanceMeters(latitude, longitude, hall.latitude, hall.longitude))
        }.sortedBy { it.meters }
    }

    fun nearestDiningHall(latitude: Double?, longitude: Double?): DiningHallDistance? =
        distancesFrom(latitude, longitude).firstOrNull()
}
