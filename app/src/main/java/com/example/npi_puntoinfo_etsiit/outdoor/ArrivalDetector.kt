package com.example.npi_puntoinfo_etsiit.outdoor

/** Zona amplia alrededor del acceso del catálogo, sin afirmar que se ha cruzado la puerta. */
data class ArrivalPolicy(
    val radiusMeters: Double = 150.0,
    val exitRadiusMeters: Double = 200.0,
    val maxAccuracyMeters: Double = 100.0,
    val requiredReadings: Int = 2,
    val minReadingIntervalMillis: Long = 2_000,
    val maxReadingGapMillis: Long = 45_000,
    val maxFixAgeMillis: Long = 60_000,
    val singleFixMaxAgeMillis: Long = 30_000,
    val maxUncertaintyMarginMeters: Double = 50.0,
    val allowSingleReading: Boolean = true
) {
    init {
        require(radiusMeters > 0 && exitRadiusMeters > radiusMeters && maxAccuracyMeters > 0)
        require(requiredReadings >= 2 && minReadingIntervalMillis > 0)
        require(maxReadingGapMillis >= minReadingIntervalMillis)
        require(maxFixAgeMillis > 0)
        require(singleFixMaxAgeMillis in 0..maxFixAgeMillis)
        require(maxUncertaintyMarginMeters >= 0 && maxUncertaintyMarginMeters.isFinite())
    }
}

/** Ofrece la transición, no certifica la entrada. Un aviso como máximo por recorrido. */
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
        // Admitir parte de la incertidumbre, sin extender indefinidamente la zona de proximidad.
        val margin = (accuracyMeters * 0.5).coerceAtMost(policy.maxUncertaintyMarginMeters)
        // Una fijación de hasta 30 s basta si su margen reducido queda dentro del radio base.
        // En el borde o con fijaciones de 30–60 s se exige corroboración temporal.
        val singleUsefulFix = policy.allowSingleReading &&
            nowMillis - fixMillis <= policy.singleFixMaxAgeMillis &&
            distanceMeters + margin <= policy.radiusMeters
        val elapsed = lastReading?.let { fixMillis - it }
        if (elapsed != null && elapsed < policy.minReadingIntervalMillis && !singleUsefulFix) return false
        if (elapsed != null && elapsed > policy.maxReadingGapMillis) {
            count = 0
            firstValidFixMillis = null
        }
        lastReading = fixMillis
        if (distanceMeters <= policy.exitRadiusMeters && distanceMeters <= policy.radiusMeters + margin) {
            if (count == 0) firstValidFixMillis = fixMillis
            count++
        } else {
            count = 0
            firstValidFixMillis = null
        }
        if (count == 0 || (!singleUsefulFix && count < policy.requiredReadings)) return false
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
