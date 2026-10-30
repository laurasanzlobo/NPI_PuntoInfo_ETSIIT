package com.example.npi_puntoinfo_etsiit.outdoor

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WalkingStep(val instruction: String, val distanceMeters: Double?, val maneuver: Maneuver = Maneuver.UNKNOWN)

enum class Maneuver {
    LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, STRAIGHT,
    ROUNDABOUT, EXIT_ROUNDABOUT, U_TURN, ARRIVAL, DEPART, KEEP_LEFT, KEEP_RIGHT, UNKNOWN
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
