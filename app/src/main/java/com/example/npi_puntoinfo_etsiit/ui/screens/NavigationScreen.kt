package com.example.npi_puntoinfo_etsiit.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.GrafoRepository
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
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

    // Instancia del grafo y cálculo de ruta mediante Dijkstra
    val grafo = remember(context) { GrafoRepository.getGrafo(context) }
    val nodoInicio = remember(grafo) { grafo.obtenerNodoInicioPredeterminado() }
    val nodoDestino = remember(grafo, activeDestination) {
        if (hasActiveRoute) grafo.buscarNodoPorDestino(activeDestination) else null
    }
    val rutaCalculada = remember(grafo, nodoInicio, nodoDestino) {
        if (nodoDestino != null) {
            grafo.calcularRutaCompleta(nodoInicio.id, nodoDestino.id)
        } else null
    }

    val currentOnPostureChanged by rememberUpdatedState(onPostureChanged)
    val currentOnSelectDestination by rememberUpdatedState(onSelectDestination)

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

        // En el centro: hueco para el mapa o la tarjeta de depuración de Dijkstra
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceWhite)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasActiveRoute && rutaCalculada != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundLight)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EtsiitBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = EtsiitBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isVerticalGuidanceActive) "Guiado Paso a Paso (Dijkstra)" else "Ruta Calculada con Dijkstra",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )

                        // Frase solicitada en medio de la pantalla
                        Text(
                            text = "Vas desde ${rutaCalculada.origen.nombre.replace('_', ' ')} hasta ${rutaCalculada.destino.nombre.replace('_', ' ')} y recorres ${rutaCalculada.nodos.size} nodos (${rutaCalculada.distanciaMetros} metros · ${rutaCalculada.tiempoEstimadoFormateado}).",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Secuencia detallada de nodos
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Secuencia de paso",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CampusTeal.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${rutaCalculada.nodos.size} nodos",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CampusTeal,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = rutaCalculada.nodos.joinToString(" → ") { it.nombre.replace('_', ' ') },
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            } else if (hasActiveRoute) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = EtsiitBlue,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Calculando ruta a $primaryTitle...",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CampusTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CampusTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Punto de partida fijado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entrada Principal (Planta Baja)",
                        fontSize = 13.sp,
                        color = CampusTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Selecciona un aula en tu Horario, el comedor o un profesor para trazar el camino más corto con Dijkstra.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
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


