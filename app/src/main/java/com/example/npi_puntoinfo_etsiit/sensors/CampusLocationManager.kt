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
)

/**
 * Gestor de ubicación y localización de campus y comedores de la UGR.
 * Permite detectar si el usuario se encuentra dentro de la ETSIIT para aplicar guiado interior
 * o si está fuera del campus para calcular mediante GPS el comedor universitario más cercano.
 */
object CampusLocationManager {

    // Coordenadas de referencia de la ETSIIT (Granada)
    const val ETSIIT_LATITUDE = 37.19708
    const val ETSIIT_LONGITUDE = -3.62446

    // Red oficial de comedores universitarios de la UGR
    val ugrDiningHalls = listOf(
        UgrDiningHall(
            id = "aynadamar",
            name = "Comedor Aynadamar",
            campus = "Campus Aynadamar",
            address = "C/ Periodista Daniel Saucedo Aranda s/n (junto a ETSIIT)",
            latitude = 37.1970,
            longitude = -3.6245,
            walkingTimeMinutes = 8
        ),
        UgrDiningHall(
            id = "fuentenueva",
            name = "Comedor Fuentenueva",
            campus = "Campus Fuentenueva",
            address = "C/ Severo Ochoa s/n",
            latitude = 37.1828,
            longitude = -3.6057,
            walkingTimeMinutes = 25
        ),
        UgrDiningHall(
            id = "cartuja",
            name = "Comedor Cartuja",
            campus = "Campus Cartuja",
            address = "Paseo de Cartuja s/n",
            latitude = 37.1930,
            longitude = -3.5985,
            walkingTimeMinutes = 35
        ),
        UgrDiningHall(
            id = "pts",
            name = "Comedor PTS",
            campus = "Campus Ciencias de la Salud",
            address = "Av. de la Ilustración s/n",
            latitude = 37.1472,
            longitude = -3.6025,
            walkingTimeMinutes = 60
        )
    )

    // Última ubicación conocida persistente en la sesión
    var lastKnownLocation: DeviceCoordinates? = null

    /**
     * Calcula o devuelve el comedor de la UGR más cercano según coordenadas GPS.
     * Si no se proporcionan coordenadas explícitas, intenta usar la última ubicación conocida
     * en la sesión de la app. Si aún no hay fijación GPS disponible, devuelve Aynadamar por defecto.
     */
    fun findNearestDiningHall(userLat: Double? = null, userLon: Double? = null): UgrDiningHall {
        val lat = userLat ?: lastKnownLocation?.latitude
        val lon = userLon ?: lastKnownLocation?.longitude
        if (lat == null || lon == null) {
            return ugrDiningHalls.first()
        }
        return ugrDiningHalls.minByOrNull { hall ->
            val dLat = hall.latitude - lat
            val dLon = hall.longitude - lon
            dLat * dLat + dLon * dLon
        } ?: ugrDiningHalls.first()
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
}

