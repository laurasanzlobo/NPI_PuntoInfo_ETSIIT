package com.example.npi_puntoinfo_etsiit.data

import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticationResult
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticator

/** Autoriza solo la reserva local de demostración. No hay proveedor de pagos ni cargos reales. */
class DiningPaymentAuthorizer(private val authenticator: DeviceAuthenticator) {
    private var generation = 0

    fun confirm(onResult: (DeviceAuthenticationResult) -> Unit) {
        val attempt = ++generation
        authenticator.authenticate { result ->
            if (generation == attempt) {
                generation++ // Ignorar también una respuesta duplicada del mismo intento.
                onResult(result)
            }
        }
    }

    fun cancel() {
        generation++ // Invalidar antes de cancelar: el sistema puede responder durante la cancelación.
        authenticator.cancel()
    }
}
