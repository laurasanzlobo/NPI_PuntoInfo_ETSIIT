package com.example.npi_puntoinfo_etsiit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Solo pruebas: evita que el bloqueo por inactividad oculte las jerarquías de Compose. */
@Composable
internal fun KeepTestScreenAwake() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}
