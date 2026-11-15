package com.example.npi_puntoinfo_etsiit.sensors

sealed interface DeviceAuthenticationResult {
    data object Authenticated : DeviceAuthenticationResult
    data object Cancelled : DeviceAuthenticationResult
    data class Error(val message: String) : DeviceAuthenticationResult
}

/** Solo recibe el resultado del sistema, nunca la credencial ni los datos biométricos. */
interface DeviceAuthenticator {
    fun authenticate(onResult: (DeviceAuthenticationResult) -> Unit)
    fun cancel()
}
