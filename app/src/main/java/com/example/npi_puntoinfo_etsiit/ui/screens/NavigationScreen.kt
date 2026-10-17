package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun NavigationScreen(
    destinationName: String? = null,
    destinationLocation: String? = null,
    isVerticalGuidanceActive: Boolean = false,
    onPostureChanged: (Boolean) -> Unit = {},
    onSelectDestination: (String, String) -> Unit = { _, _ -> },
    onClearRoute: () -> Unit = {}
) {
    val context = LocalContext.current
    val hasActiveRoute = !destinationName.isNullOrBlank()
    val activeDestination = destinationName ?: ""
    val activeLocation = destinationLocation ?: ""

    // Si el destino viene con paréntesis (ej. "Despacho D-08 (Alberto Ruiz)"),
    // separamos el título principal ("Despacho D-08") del detalle/persona ("Alberto Ruiz")
    // para mostrarlo en dos líneas diferenciadas y limpias sin paréntesis.
    val (primaryTitle, extractedDetail) = remember(activeDestination) {
        if (activeDestination.contains("(") && activeDestination.contains(")")) {
            val title = activeDestination.substringBefore("(").trim()
            val extra = activeDestination.substringAfter("(").substringBefore(")").trim()
            title to extra
        } else {
            activeDestination to ""
        }
    }

    val subtitleText = remember(extractedDetail, activeLocation) {
        when {
            extractedDetail.isNotBlank() && activeLocation.isNotBlank() -> "$extractedDetail · $activeLocation"
            extractedDetail.isNotBlank() -> extractedDetail
            activeLocation.isNotBlank() -> activeLocation
            else -> "Ruta activa"
        }
    }

    val currentOnPostureChanged by rememberUpdatedState(onPostureChanged)

    // Acelerómetro: detecta cuando se levanta el móvil verticalmente para activar el guiado paso a paso,
    // y cuando se baja de nuevo para volver a mostrar el mapa
    DisposableEffect(context, hasActiveRoute) {
        val manager = AccelerometerManager(context)

        if (hasActiveRoute) {
            manager.onPostureModeChanged = { isVertical ->
                triggerVibration(context, durationMs = 70)
                currentOnPostureChanged(isVertical)
            }

            manager.startListening()
        }

        onDispose {
            manager.stopListening()
            currentOnPostureChanged(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasActiveRoute) {
            // Arriba: dónde vamos (se adapta dinámicamente si se levanta el terminal a Guiado Paso a Paso)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    (if (isVerticalGuidanceActive) EtsiitBlue else UgrRed).copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVerticalGuidanceActive) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.Place,
                                contentDescription = null,
                                tint = if (isVerticalGuidanceActive) EtsiitBlue else UgrRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = primaryTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Loguito del gesto de postura (levantar el móvil)
                        GestureBadge(
                            type = GestureType.LIFT_PHONE,
                            onClick = { onPostureChanged(!isVerticalGuidanceActive) }
                        )

                        // Botón para finalizar / cancelar la ruta activa
                        IconButton(onClick = onClearRoute) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar ruta",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }


        }

        // En el centro: hueco vacío reservado para el mapa (postura normal) o para la foto del paso a paso (móvil levantado)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceWhite)
        )
    }
}

/**
 * Función auxiliar para emitir una vibración háptica suave al cambiar de postura
 */
private fun triggerVibration(context: Context, durationMs: Long = 80) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}


