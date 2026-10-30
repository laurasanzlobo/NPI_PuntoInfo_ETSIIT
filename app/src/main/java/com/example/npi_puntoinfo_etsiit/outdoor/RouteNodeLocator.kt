package com.example.npi_puntoinfo_etsiit.outdoor

/** Punto GPS guardado y paso al que pertenece. No es un nodo de búsqueda de caminos. */
data class RouteGuidanceNode(val id: String, val point: GeoPoint, val stepIndex: Int)
data class NearestRouteNode(val node: RouteGuidanceNode, val distanceMeters: Double)

/** Comparación directa con los puntos locales: Haversine a cada nodo y elección del mínimo. */
class RouteNodeLocator(route: FixedOutdoorRoute) {
    // Se reutilizan las 48 coordenadas existentes, sin cambiar el trazado ni inventar puntos.
    // En una unión, el nodo pertenece al paso que empieza allí, no al que ya termina.
    val nodes: List<RouteGuidanceNode> = if (!route.gpsReady) emptyList() else
        route.waypoints.flatMapIndexed { index, waypoint ->
            val points = if (index == route.waypoints.lastIndex) listOf(waypoint.point!!)
                else waypoint.pathToNext.dropLast(1).ifEmpty { listOf(waypoint.point!!) }
            points.mapIndexed { offset, point -> RouteGuidanceNode("${waypoint.id}-$offset", point, index) }
        }

    fun nearest(point: GeoPoint, stepIndex: Int? = null): NearestRouteNode? {
        require(point.isValid)
        return nodes.asSequence().filter { stepIndex == null || it.stepIndex == stepIndex }
            .map { NearestRouteNode(it, RouteGeometry.distance(point, it.point)) }
            .minByOrNull { it.distanceMeters }
    }
}
