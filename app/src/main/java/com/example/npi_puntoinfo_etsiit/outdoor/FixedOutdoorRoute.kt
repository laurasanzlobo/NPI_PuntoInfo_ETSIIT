package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager

/** Una instrucción se ejecuta en su waypoint; el siguiente waypoint es el objetivo del tramo. */
data class RouteWaypoint(
    val id: String,
    val point: GeoPoint?,
    val instruction: String,
    val maneuver: Maneuver,
    val distanceToNextMeters: Double? = null,
    val landmark: String? = null,
    /** Geometría local del tramo, incluidos ambos extremos; nunca se dibuja un mapa. */
    val pathToNext: List<GeoPoint> = emptyList(),
    /** Texto para quien ya ha pasado el giro y está recorriendo este tramo. */
    val continuationInstruction: String? = null
)

data class FixedOutdoorRoute(
    val id: String,
    val name: String,
    val waypoints: List<RouteWaypoint>,
    val fieldValidated: Boolean,
    val cartographyVerified: Boolean = false
) {
    init {
        require(waypoints.size >= 2)
        require(waypoints.map { it.id }.distinct().size == waypoints.size)
        require(waypoints.all { it.id.isNotBlank() && it.instruction.isNotBlank() &&
            (it.point == null || it.point.isValid) &&
            (it.distanceToNextMeters == null || it.distanceToNextMeters.isFinite() && it.distanceToNextMeters >= 0) })
        require(waypoints.last().maneuver == Maneuver.ARRIVAL)
        require(waypoints.all { it.pathToNext.all { point -> point.isValid } })
        require(!(fieldValidated || cartographyVerified) || waypoints.all { it.point != null })
        require(!(fieldValidated || cartographyVerified) || waypoints.zipWithNext().all { (a, b) -> a.point != b.point })
        waypoints.zipWithNext().forEach { (a, b) ->
            require(a.pathToNext.isEmpty() || a.pathToNext.size >= 2 && a.pathToNext.first() == a.point && a.pathToNext.last() == b.point)
        }
    }

    val gpsReady: Boolean get() = (fieldValidated || cartographyVerified) && waypoints.all { it.point != null }
}

object EtsiitAuxiliaryRoute {
    const val destinationId = "auxiliary-etsiit"
    const val unavailableMessage = "Este recorrido exterior no está disponible. El itinerario disponible conecta la ETSIIT con su edificio auxiliar."

    // Referencia histórica del catálogo, NO una puerta validada; se conserva para compatibilidad.
    val catalogReference = GeoPoint(CampusLocationManager.ETSIIT_LATITUDE, CampusLocationManager.ETSIIT_LONGITUDE)

    // Consulta cartográfica: Google Maps, 2026-10-30, modo A PIE, lugares identificados por nombre.
    // Origen: E.T.S. de Ingenierías Informática y de Telecomunicación (NO Comedor Aynadamar).
    // Destino: Edificio Auxiliar de la ETSIIT, C. Periodista Juan Osorio Bueno.
    // Ref.: docs/GUIADO_EXTERIOR_FIJO.md. Datos estáticos: nunca se consulta Google desde la app.
    val mainBuildingReference = GeoPoint(37.197055, -3.6245507)
    val auxiliaryBuildingReference = GeoPoint(37.1980819, -3.6297409)

    // Puntos del trazado peatonal consultado. Conservan la curva de la rotonda y de las calles;
    // no son una línea recta inventada entre los edificios. El extremo es el acceso desde la calle,
    // no el centro del inmueble ni una certificación de haber cruzado la puerta.
    private val path = listOf(
        GeoPoint(37.1967175, -3.6247391),
        GeoPoint(37.1972235, -3.6250450),
        GeoPoint(37.1974308, -3.6251570),
        GeoPoint(37.1974838, -3.6251857),
        GeoPoint(37.1974934, -3.6251883),
        GeoPoint(37.1975069, -3.6251931),
        GeoPoint(37.1975571, -3.6252030),
        GeoPoint(37.1976567, -3.6252096),
        GeoPoint(37.1976599, -3.6253105),
        GeoPoint(37.1976147, -3.6254305),
        GeoPoint(37.1976479, -3.6258411),
        GeoPoint(37.1976558, -3.6259339),
        GeoPoint(37.1976370, -3.6259389),
        GeoPoint(37.1976227, -3.6259444),
        GeoPoint(37.1976088, -3.6259514),
        GeoPoint(37.1975954, -3.6259601),
        GeoPoint(37.1975696, -3.6259801),
        GeoPoint(37.1975421, -3.6260244),
        GeoPoint(37.1975266, -3.6260544),
        GeoPoint(37.1975163, -3.6261097),
        GeoPoint(37.1975144, -3.6261276),
        GeoPoint(37.1975136, -3.6261458),
        GeoPoint(37.1975142, -3.6261641),
        GeoPoint(37.1975173, -3.6261969),
        GeoPoint(37.1975403, -3.6262539),
        GeoPoint(37.1975654, -3.6262895),
        GeoPoint(37.1975756, -3.6263014),
        GeoPoint(37.1975867, -3.6263122),
        GeoPoint(37.1976053, -3.6263271),
        GeoPoint(37.1976223, -3.6263360),
        GeoPoint(37.1976189, -3.6264288),
        GeoPoint(37.1976106, -3.6265295),
        GeoPoint(37.1976333, -3.6265827),
        GeoPoint(37.1975533, -3.6272891),
        GeoPoint(37.1975175, -3.6278797),
        GeoPoint(37.1974846, -3.6281970),
        GeoPoint(37.1974547, -3.6284849),
        GeoPoint(37.1974339, -3.6288598),
        GeoPoint(37.1974181, -3.6291601),
        GeoPoint(37.1974115, -3.6292728),
        GeoPoint(37.1974062, -3.6293663),
        GeoPoint(37.1973962, -3.6295600),
        GeoPoint(37.1973946, -3.6298439),
        GeoPoint(37.1974821, -3.6298693),
        GeoPoint(37.1975230, -3.6298931),
        GeoPoint(37.1975531, -3.6299168),
        GeoPoint(37.1976794, -3.6300165),
        GeoPoint(37.1978519, -3.6301486)
    )

    // Único recorrido activo: los mismos puntos peatonales en sentido auxiliar → ETSIIT.
    // Se separa la llegada a la rotonda de su recorrido para no anticipar esa maniobra.
    private val returnPath = path.reversed()
    private fun sectionLength(from: Int, through: Int): Double = returnPath.subList(from, through + 1)
        .zipWithNext().sumOf { (a, b) -> RouteGeometry.distance(a, b) }

    val route = FixedOutdoorRoute(destinationId, "Edificio auxiliar → ETSIIT", listOf(
        RouteWaypoint("auxiliary-access", returnPath[0],
            "Desde el acceso del edificio auxiliar, continúa unos 58 metros por C. Periodista Juan Osorio Bueno hasta C. Periodista Eduardo Molina Fajardo.", Maneuver.DEPART,
            distanceToNextMeters = 58.0, landmark = "Inicio en el acceso desde la calle, no en el centro del inmueble.",
            pathToNext = returnPath.subList(0, 6),
            continuationInstruction = "Continúa por C. Periodista Juan Osorio Bueno hasta el cruce con C. Periodista Eduardo Molina Fajardo."),
        RouteWaypoint("eduardo-molina", returnPath[5],
            "Gira a la izquierda por C. Periodista Eduardo Molina Fajardo y continúa hasta la rotonda.", Maneuver.LEFT,
            distanceToNextMeters = sectionLength(5, 15), pathToNext = returnPath.subList(5, 16),
            continuationInstruction = "Continúa por C. Periodista Eduardo Molina Fajardo hasta la rotonda."),
        RouteWaypoint("roundabout", returnPath[15],
            "Sigue el itinerario peatonal alrededor de la rotonda hasta C. Periodista Rafael Gómez Mont.", Maneuver.ROUNDABOUT,
            distanceToNextMeters = sectionLength(15, 36), landmark = "Respeta los pasos peatonales y la señalización.",
            pathToNext = returnPath.subList(15, 37),
            continuationInstruction = "Continúa por el itinerario peatonal de la rotonda hacia C. Periodista Rafael Gómez Mont."),
        RouteWaypoint("rafael-gomez", returnPath[36],
            "Continúa 61 metros por C. Periodista Rafael Gómez Mont hasta el giro hacia el acceso de la ETSIIT.", Maneuver.STRAIGHT,
            distanceToNextMeters = 61.0, pathToNext = returnPath.subList(36, 41),
            continuationInstruction = "Continúa por C. Periodista Rafael Gómez Mont hasta el giro hacia el acceso de la ETSIIT."),
        RouteWaypoint("principal-approach", returnPath[40],
            "Gira a la derecha hacia el acceso peatonal de la ETSIIT y continúa unos 110 metros.", Maneuver.RIGHT,
            distanceToNextMeters = 108.0, pathToNext = returnPath.subList(40, 48),
            continuationInstruction = "Continúa hacia el acceso peatonal del edificio principal de la ETSIIT."),
        RouteWaypoint("principal-access", returnPath.last(),
            "Has llegado al acceso del edificio principal de la ETSIIT. Puedes continuar con el guiado interior.", Maneuver.ARRIVAL)
    ), fieldValidated = false, cartographyVerified = true)
}
