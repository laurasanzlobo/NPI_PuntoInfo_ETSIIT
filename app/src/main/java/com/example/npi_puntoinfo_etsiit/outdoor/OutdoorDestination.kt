package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances

data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = OutdoorDistances.validCoordinates(latitude, longitude)
}

/** El motor solo necesita un identificador, una etiqueta y coordenadas, no un nodo interior. */
data class OutdoorDestination(val id: String, val name: String, val description: String, val point: GeoPoint)

object OutdoorDestinations {
    val all: List<OutdoorDestination> = CampusLocationManager.ugrDiningHalls.map { hall ->
        OutdoorDestination("dining-${hall.id}", hall.name, hall.address,
            GeoPoint(hall.latitude, hall.longitude))
    } + OutdoorDestination("etsiit", "ETSIIT", "Campus Aynadamar",
        GeoPoint(CampusLocationManager.ETSIIT_LATITUDE, CampusLocationManager.ETSIIT_LONGITUDE))

    fun find(id: String?): OutdoorDestination? = all.firstOrNull { it.id == id }
    fun forDiningHall(id: String): OutdoorDestination? = find("dining-$id")

    /** Evita que el resolver de voz histórico convierta «Comedor PTS» en Comedor_PSS. */
    fun diningNamedIn(query: String): OutdoorDestination? = CampusLocationManager.ugrDiningHalls
        .firstOrNull { Regex("\\bcomedor(?:\\s+de)?\\s+${Regex.escape(it.name.removePrefix("Comedor "))}\\b",
            RegexOption.IGNORE_CASE).containsMatchIn(query) }
        ?.let { forDiningHall(it.id) }
}

/** Los argumentos de destino final sobreviven a recreaciones y al regreso desde el mapa interior. */
object OutdoorNavigationRoute {
    const val base = "outdoor"
    const val pattern = "outdoor?destinationId={destinationId}&finalDestination={finalDestination}&finalLocation={finalLocation}&finalNodeId={finalNodeId}"
    fun create(destinationId: String): String {
        require(destinationId.matches(Regex("[a-zA-Z0-9_-]+")))
        return "$base?destinationId=$destinationId"
    }

    fun forProfessor(destination: String, location: String): String {
        return forIndoorTarget(destination, location)
    }

    fun forIndoorTarget(destination: String, location: String, nodeId: String? = null): String {
        fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return create(EtsiitAccess.outdoorId) + "&finalDestination=${encode(destination)}&finalLocation=${encode(location)}" +
            (nodeId?.let { "&finalNodeId=${encode(it)}" } ?: "")
    }
}
