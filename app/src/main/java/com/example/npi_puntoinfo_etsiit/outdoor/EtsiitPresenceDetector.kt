package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates

enum class EtsiitPresence { UNKNOWN, NEAR_BUILDING, OUTSIDE }

/** Referencia del edificio principal comprobada en Google Maps, distinta del comedor.
 * Es una zona de proximidad, no una afirmación de estar dentro de un edificio. */
data class PresencePolicy(
    val reference: GeoPoint = EtsiitAuxiliaryRoute.mainBuildingReference,
    val nearRadiusMeters: Double = 80.0,
    val outsideRadiusMeters: Double = 130.0,
    val maxAccuracyMeters: Double = 25.0,
    val maxFixAgeMillis: Long = 10_000,
    val minIntervalMillis: Long = 1_500,
    val maxGapMillis: Long = 8_000,
    val requiredReadings: Int = 3,
    val updateIntervalMillis: Long = 2_000
) {
    init {
        require(reference.isValid)
        require(listOf(nearRadiusMeters, outsideRadiusMeters, maxAccuracyMeters).all { it.isFinite() && it > 0 })
        require(outsideRadiusMeters > nearRadiusMeters && requiredReadings >= 2)
        require(maxFixAgeMillis > 0 && minIntervalMillis > 0 && maxGapMillis >= minIntervalMillis && updateIntervalMillis > 0)
    }
}

class EtsiitPresenceDetector(val policy: PresencePolicy = PresencePolicy()) {
    var presence = EtsiitPresence.UNKNOWN
        private set
    private var lastTime: Long? = null
    private var candidate = EtsiitPresence.UNKNOWN
    private var count = 0

    fun reset() { presence = EtsiitPresence.UNKNOWN; lastTime = null; clearEvidence() }
    fun clearEvidence() { candidate = EtsiitPresence.UNKNOWN; count = 0 }

    fun observe(fix: DeviceCoordinates, nowMillis: Long): EtsiitPresence {
        val time = fix.elapsedRealtimeMillis
        if (!fix.isValid || time == null || time < 0 || nowMillis - time !in 0..policy.maxFixAgeMillis ||
            fix.accuracyMeters > policy.maxAccuracyMeters) {
            clearEvidence()
            return presence
        }
        val elapsed = lastTime?.let { time - it }
        if (elapsed != null && elapsed < policy.minIntervalMillis) return presence
        if (elapsed != null && elapsed > policy.maxGapMillis) clearEvidence()
        lastTime = time
        val distance = RouteGeometry.distance(GeoPoint(fix.latitude, fix.longitude), policy.reference)
        val reading = when {
            distance - fix.accuracyMeters > policy.outsideRadiusMeters -> EtsiitPresence.OUTSIDE
            distance + fix.accuracyMeters < policy.nearRadiusMeters -> EtsiitPresence.NEAR_BUILDING
            else -> EtsiitPresence.UNKNOWN
        }
        if (reading == EtsiitPresence.UNKNOWN) { clearEvidence(); return presence }
        if (candidate != reading) { candidate = reading; count = 0 }
        count++
        if (count >= policy.requiredReadings) presence = reading
        return presence
    }
}
