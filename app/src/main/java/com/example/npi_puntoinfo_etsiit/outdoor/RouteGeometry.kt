package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import kotlin.math.cos
import kotlin.math.hypot

data class SegmentMatch(val index: Int, val fraction: Double, val distanceMeters: Double)

object RouteGeometry {
    fun distance(a: GeoPoint, b: GeoPoint): Double =
        OutdoorDistances.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Proyección equirectangular local en metros, suficiente para tramos peatonales cortos. */
    fun project(point: GeoPoint, a: GeoPoint, b: GeoPoint, index: Int = 0): SegmentMatch {
        require(point.isValid && a.isValid && b.isValid)
        val scale = 6_371_008.8 * Math.PI / 180
        val longitudeScale = scale * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val x = (point.longitude - a.longitude) * longitudeScale
        val y = (point.latitude - a.latitude) * scale
        val dx = (b.longitude - a.longitude) * longitudeScale
        val dy = (b.latitude - a.latitude) * scale
        val lengthSquared = dx * dx + dy * dy
        val fraction = if (lengthSquared == 0.0) 0.0 else ((x * dx + y * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return SegmentMatch(index, fraction, hypot(x - fraction * dx, y - fraction * dy))
    }

    fun nearest(point: GeoPoint, route: FixedOutdoorRoute): SegmentMatch? =
        if (!route.gpsReady) null else (0 until route.waypoints.lastIndex).map { index ->
            projectOnStep(point, route, index)
        }.minByOrNull { it.distanceMeters }

    private fun stepPoints(route: FixedOutdoorRoute, index: Int): List<GeoPoint> =
        route.waypoints[index].pathToNext.ifEmpty {
            listOf(route.waypoints[index].point!!, route.waypoints[index + 1].point!!)
        }

    fun stepLength(route: FixedOutdoorRoute, index: Int): Double =
        stepPoints(route, index).zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** Progreso sobre los subsegmentos del MISMO paso (p. ej. recorrido de una rotonda). */
    fun projectOnStep(point: GeoPoint, route: FixedOutdoorRoute, index: Int): SegmentMatch {
        val points = stepPoints(route, index)
        val total = stepLength(route, index)
        var before = 0.0
        return points.zipWithNext().map { (a, b) ->
            val length = distance(a, b)
            val match = project(point, a, b, index)
            val fraction = if (total == 0.0) 0.0 else (before + match.fraction * length) / total
            before += length
            match.copy(fraction = fraction)
        }.minBy { it.distanceMeters }
    }
}
