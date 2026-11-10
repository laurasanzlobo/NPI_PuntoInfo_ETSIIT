package com.example.npi_puntoinfo_etsiit.sensors

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.npi_puntoinfo_etsiit.data.DiningReservationPolicy

@Composable
fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val context = LocalContext.current
    val credentialResult = remember { CredentialResult() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = credentialResult.callback
        credentialResult.callback = null
        callback?.invoke(if (result.resultCode == Activity.RESULT_OK)
            DeviceAuthenticationResult.Authenticated else DeviceAuthenticationResult.Cancelled)
    }
    val authenticator = remember(context, launcher) {
        AndroidDeviceAuthenticator(context) { intent, callback ->
            credentialResult.callback = callback
            launcher.launch(intent)
        }
    }
    DisposableEffect(authenticator) {
        onDispose {
            credentialResult.callback = null
            authenticator.cancel()
        }
    }
    return authenticator
}

private class CredentialResult {
    var callback: ((DeviceAuthenticationResult) -> Unit)? = null
}

private class AndroidDeviceAuthenticator(
    private val context: Context,
    private val launchCredential: (Intent, (DeviceAuthenticationResult) -> Unit) -> Unit
) : DeviceAuthenticator {
    private var signal: CancellationSignal? = null
    private var generation = 0
    private var callback: ((DeviceAuthenticationResult) -> Unit)? = null

    override fun authenticate(onResult: (DeviceAuthenticationResult) -> Unit) {
        cancel()
        val attempt = generation
        callback = onResult
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            complete(attempt, DeviceAuthenticationResult.Error(
                "Configura un PIN, patrón o contraseña de bloqueo en el móvil para confirmar la reserva."
            ))
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) showBiometrics(attempt)
            else showCredential(attempt)
        } catch (_: Exception) {
            complete(attempt, DeviceAuthenticationResult.Error("No se ha podido abrir la autenticación del móvil. Inténtalo de nuevo."))
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Suppress("DEPRECATION")
    private fun showBiometrics(attempt: Int) {
        val builder = BiometricPrompt.Builder(context)
            .setTitle("Confirmar reserva")
            .setSubtitle("Menú para llevar · ${DiningReservationPolicy.PRICE_LABEL}")
            .setDescription("Verifica tu identidad con el bloqueo del móvil para confirmar la reserva.")
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton("Usar PIN o patrón", context.mainExecutor) { _, _ ->
                if (attempt == generation) showCredential(attempt)
            }
        }
        signal = CancellationSignal()
        builder.build().authenticate(signal!!, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                complete(attempt, DeviceAuthenticationResult.Authenticated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (attempt != generation) return
                // Android 9 necesita abrir la alternativa de bloqueo por separado.
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && errorCode in listOf(
                        BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
                        BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT, BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT)) {
                    showCredential(attempt)
                    return
                }
                val result = if (errorCode in listOf(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
                        BiometricPrompt.BIOMETRIC_ERROR_CANCELED))
                    DeviceAuthenticationResult.Cancelled
                else DeviceAuthenticationResult.Error("No se ha podido verificar tu identidad: $errString")
                complete(attempt, result)
            }
            // Una huella/cara no reconocida permite reintentar dentro del diálogo del sistema.
        })
    }

    @Suppress("DEPRECATION")
    private fun showCredential(attempt: Int) {
        try {
            val intent = context.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(
                "Confirmar reserva", "Verifica tu identidad para confirmar la reserva de ${DiningReservationPolicy.PRICE_LABEL}."
            )
            if (intent == null) {
                complete(attempt, DeviceAuthenticationResult.Error("El bloqueo del móvil no está disponible. No se ha reservado el menú."))
            } else {
                launchCredential(intent) { complete(attempt, it) }
            }
        } catch (_: Exception) {
            complete(attempt, DeviceAuthenticationResult.Error("No se ha podido abrir el bloqueo del móvil. No se ha reservado el menú."))
        }
    }

    private fun complete(attempt: Int, result: DeviceAuthenticationResult) {
        if (attempt != generation) return
        val current = callback ?: return
        callback = null
        signal = null
        current(result)
    }

    override fun cancel() {
        generation++
        callback = null
        signal?.cancel()
        signal = null
    }
}
