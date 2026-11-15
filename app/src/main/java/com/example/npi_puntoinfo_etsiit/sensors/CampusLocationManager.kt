package com.example.npi_puntoinfo_etsiit.sensors

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
 * Permite detectar si el usuario se encuentra dentro de la ETSIIT para aplicar guiado interior
 * o si está fuera del campus para calcular mediante GPS el comedor universitario más cercano.
 */
object CampusLocationManager {

    // Coordenadas de referencia de la ETSIIT (Granada)
    const val ETSIIT_LATITUDE = 37.19708
    const val ETSIIT_LONGITUDE = -3.62446

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
}

