package com.example.npi_puntoinfo_etsiit.outdoor

import android.content.Context

/** Solo conserva la versión aceptada de este consentimiento, nunca coordenadas ni destinos. */
class RouteConsentStore(context: Context) {
    private val preferences = context.getSharedPreferences("outdoor_privacy", Context.MODE_PRIVATE)

    val isGranted: Boolean get() = preferences.getInt("ors_consent_version", 0) == 1

    fun grant() {
        preferences.edit().putInt("ors_consent_version", 1).apply()
    }
}
