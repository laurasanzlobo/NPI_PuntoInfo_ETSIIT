package com.example.npi_puntoinfo_etsiit.sensors

import com.example.npi_puntoinfo_etsiit.normalizarPlanta
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Bloques principales conectados de la ETSIIT detectables mediante GPS:
 * - [AULAS]: Edificio alargado de aulas y laboratorios (`edificio = "laboratorios"` en el grafo).
 * - [ZONAS_COMUNES]: Edificio principal de zonas comunes, secretaría, biblioteca y despachos (`edificio = "despachos"` en el grafo).
 */
enum class EtsiitBuilding(val graphCode: String) {
    AULAS("laboratorios"),
    ZONAS_COMUNES("despachos")
}

/**
 * Modelo de datos representativo de un comedor universitario de la Universidad de Granada (UGR).
 */
data class UgrDiningHall(
    val id: String,
    val name: String,
    val campus: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val walkingTimeMinutes: Int
) {
    fun getName(isEnglish: Boolean = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)): String {
        return if (isEnglish) "Aynadamar Dining Hall" else name
    }
}

/**
 * Gestor de ubicación y localización de campus y comedores de la UGR.
 * Permite detectar si el usuario se encuentra dentro de la ETSIIT para aplicar guiado interior,
 * distinguir en qué edificio de la ETSIIT se encuentra mediante GPS y calcular el comedor más cercano.
 */
object CampusLocationManager {

    // Coordenadas de referencia de la ETSIIT (Granada)
    const val ETSIIT_LATITUDE = 37.19708
    const val ETSIIT_LONGITUDE = -3.62446

    // Extremos del segmento longitudinal del edificio de las aulas y laboratorios
    private val AULAS_SEGMENT_START = 37.197402 to -3.624741
    private val AULAS_SEGMENT_END = 37.197338 to -3.623963

    // Vértices de referencia del edificio de zonas comunes y despachos (ordenados en sentido horario: NO, NE, SE, SO)
    private val ZONAS_COMUNES_POLYGON = listOf(
        37.197207 to -3.624615,
        37.197152 to -3.624388,
        37.196880 to -3.624413,
        37.196938 to -3.624699
    )

    // Red oficial de comedores universitarios de la UGR (simulación centrada en Aynadamar / ETSIIT)
    val ugrDiningHalls = listOf(
        UgrDiningHall(
            id = "aynadamar",
            name = "Comedor Aynadamar",
            campus = "Campus Aynadamar",
            address = "C/ Periodista Daniel Saucedo Aranda s/n (junto a ETSIIT)",
            latitude = 37.1970,
            longitude = -3.6245,
            walkingTimeMinutes = 8
        )
    )

    // Última ubicación conocida persistente en la sesión
    var lastKnownLocation: DeviceCoordinates? = null

    /**
     * Devuelve el comedor más cercano (en esta simulación siempre Aynadamar).
     */
    fun findNearestDiningHall(userLat: Double? = null, userLon: Double? = null): UgrDiningHall {
        return ugrDiningHalls.first()
    }

    /**
     * Construye los parámetros de guiado (destino y descripción de ubicación) en función
     * de si el usuario se encuentra dentro de la ETSIIT o en el exterior (GPS).
     */
    fun getDiningNavigationRoute(isInsideEtsiit: Boolean, userLat: Double? = null, userLon: Double? = null): Pair<String, String> {
        return if (isInsideEtsiit) {
            Pair(
                "Comedor Aynadamar",
                "ETSIIT · Planta Baja (Guiado Interior hacia salida)"
            )
        } else {
            val nearest = findNearestDiningHall(userLat, userLon)
            Pair(
                nearest.name,
                "Exterior · ${nearest.campus} (GPS)"
            )
        }
    }

    /**
     * Comprueba si la ubicación del usuario (instantánea o última conocida) está aproximadamente
     * dentro del ámbito de la ETSIIT (por defecto dentro de un radio de 400 metros).
     * Si no se dispone de señal GPS fija o permisos, se asume `true` por defecto para facilitar
     * las pruebas y el guiado interior.
     */
    fun isUserNearEtsiit(context: android.content.Context, maxRadiusMeters: Double = 400.0): Boolean {
        val instant = DeviceLocationProvider.getInstantLocation(context)
        val lat = instant?.latitude ?: lastKnownLocation?.latitude
        val lon = instant?.longitude ?: lastKnownLocation?.longitude
        if (lat == null || lon == null) {
            return true
        }
        val distance = OutdoorDistances.distanceMeters(lat, lon, ETSIIT_LATITUDE, ETSIIT_LONGITUDE)
        return distance <= maxRadiusMeters
    }

    /**
     * Determina en cuál de los dos edificios conectados de la ETSIIT se encuentra el usuario
     * combinando la planta estimada y las coordenadas GPS:
     * - Edificio de las aulas: distancia al segmento entre (37.197402, -3.624741) y (37.197338, -3.623963).
     * - Edificio de zonas comunes: distancia al polígono/puntos de referencia del bloque principal.
     */
    fun estimateEtsiitBuilding(
        userLat: Double?,
        userLon: Double?,
        floorCode: String = "PB"
    ): EtsiitBuilding {
        // La planta semisótano (PSS / P-1) pertenece al edificio de zonas comunes
        if (normalizarPlanta(floorCode) == "PSS") {
            return EtsiitBuilding.ZONAS_COMUNES
        }

        if (userLat == null || userLon == null) {
            return EtsiitBuilding.AULAS
        }

        val distToAulas = distanceToSegmentMeters(
            lat = userLat,
            lon = userLon,
            segStart = AULAS_SEGMENT_START,
            segEnd = AULAS_SEGMENT_END
        )

        val distToZonasComunes = distanceToPolygonMeters(
            lat = userLat,
            lon = userLon,
            polygon = ZONAS_COMUNES_POLYGON
        )

        return if (distToAulas <= distToZonasComunes) {
            EtsiitBuilding.AULAS
        } else {
            EtsiitBuilding.ZONAS_COMUNES
        }
    }

    private fun toLocalMeters(lat: Double, lon: Double): Pair<Double, Double> {
        val refLat = ETSIIT_LATITUDE
        val refLon = ETSIIT_LONGITUDE
        val x = (lon - refLon) * cos(Math.toRadians(refLat)) * 111_320.0
        val y = (lat - refLat) * 111_132.0
        return x to y
    }

    private fun distanceToSegmentMeters(
        lat: Double,
        lon: Double,
        segStart: Pair<Double, Double>,
        segEnd: Pair<Double, Double>
    ): Double {
        val (px, py) = toLocalMeters(lat, lon)
        val (ax, ay) = toLocalMeters(segStart.first, segStart.second)
        val (bx, by) = toLocalMeters(segEnd.first, segEnd.second)

        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 1e-9) {
            return hypot(px - ax, py - ay)
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return hypot(px - projX, py - projY)
    }

    private fun distanceToPolygonMeters(
        lat: Double,
        lon: Double,
        polygon: List<Pair<Double, Double>>
    ): Double {
        if (isPointInPolygon(lat, lon, polygon)) {
            return 0.0
        }
        var minDistance = Double.MAX_VALUE
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val d = distanceToSegmentMeters(lat, lon, a, b)
            if (d < minDistance) {
                minDistance = d
            }
        }
        return minDistance
    }

    private fun isPointInPolygon(
        lat: Double,
        lon: Double,
        polygon: List<Pair<Double, Double>>
    ): Boolean {
        val (px, py) = toLocalMeters(lat, lon)
        val pts = polygon.map { toLocalMeters(it.first, it.second) }
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val (xi, yi) = pts[i]
            val (xj, yj) = pts[j]
            val intersects = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-9) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}

