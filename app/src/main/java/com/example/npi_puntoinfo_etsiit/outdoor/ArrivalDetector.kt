package com.example.npi_puntoinfo_etsiit.outdoor

data class ArrivalPolicy(
    val radiusMeters: Double = 45.0,
    val exitRadiusMeters: Double = 90.0,
    val maxAccuracyMeters: Double = 25.0,
    val requiredReadings: Int = 2,
    val minReadingIntervalMillis: Long = 2_000,
    val maxReadingGapMillis: Long = 45_000,
    val maxFixAgeMillis: Long = 10_000
) {
    init {
        require(radiusMeters > 0 && exitRadiusMeters > radiusMeters && maxAccuracyMeters > 0)
        require(requiredReadings >= 2 && minReadingIntervalMillis > 0)
        require(maxReadingGapMillis >= minReadingIntervalMillis)
        require(maxFixAgeMillis > 0)
    }
}

/** Lecturas fiables separadas en el tiempo. Un aviso como máximo por recorrido, incluso tras rechazarlo. */
class ArrivalDetector(val policy: ArrivalPolicy = ArrivalPolicy(), alreadyNotified: Boolean = false) {
    var notified = alreadyNotified
        private set
    private var count = 0
    private var lastReading: Long? = null
    var firstValidFixMillis: Long? = null
        private set
    var confirmedAtMillis: Long? = null
        private set

    fun observe(distanceMeters: Double, accuracyMeters: Double, fixMillis: Long, nowMillis: Long = fixMillis): Boolean {
        if (notified) return false
        if (fixMillis < 0 || !distanceMeters.isFinite() || distanceMeters < 0 || !accuracyMeters.isFinite() ||
            accuracyMeters < 0 || accuracyMeters > policy.maxAccuracyMeters) {
            count = 0
            firstValidFixMillis = null
            return false
        }
        // No contar de nuevo la misma fijación al recomponer/reanudar o cambiar de frecuencia.
        if (lastReading?.let { fixMillis <= it } == true) return false
        if (nowMillis - fixMillis !in 0..policy.maxFixAgeMillis) {
            count = 0
            firstValidFixMillis = null
            return false
        }
        val elapsed = lastReading?.let { fixMillis - it }
        if (elapsed != null && elapsed < policy.minReadingIntervalMillis) return false
        if (elapsed != null && elapsed > policy.maxReadingGapMillis) {
            count = 0
            firstValidFixMillis = null
        }
        lastReading = fixMillis
        if (distanceMeters - accuracyMeters >= policy.exitRadiusMeters) count = 0
        // El margen de error también debe quedar dentro del radio: no basta una coordenada imprecisa.
        if (distanceMeters + accuracyMeters <= policy.radiusMeters) {
            if (count == 0) firstValidFixMillis = fixMillis
            count++
        } else {
            count = 0
            firstValidFixMillis = null
        }
        if (count < policy.requiredReadings) return false
        notified = true
        confirmedAtMillis = nowMillis
        return true
    }
}

/** Solo aumenta la frecuencia al acercarse al acceso; no altera los umbrales de llegada. */
object ActiveLocationPolicy {
    const val cruisingIntervalMillis = 12_000L
    const val approachingIntervalMillis = 2_000L
    const val approachRadiusMeters = 150.0

    fun interval(distanceMeters: Double, accuracyMeters: Double): Long =
        if (distanceMeters.isFinite() && accuracyMeters.isFinite() &&
            distanceMeters - accuracyMeters <= approachRadiusMeters) approachingIntervalMillis
        else cruisingIntervalMillis
}
