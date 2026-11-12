package com.example.npi_puntoinfo_etsiit.outdoor

/** Presentación por recorrido. No sustituye ni modifica las lecturas de ArrivalDetector. */
enum class ArrivalPresentation {
    WAITING, PROMPT, SHOWN, DEFERRED, CONTINUED, RETURNED;

    fun onArrival(hasIndoorTarget: Boolean): ArrivalPresentation =
        if (hasIndoorTarget && this == WAITING) PROMPT else this

    fun defer(): ArrivalPresentation = DEFERRED
    fun continueIndoors(): ArrivalPresentation = CONTINUED
}
