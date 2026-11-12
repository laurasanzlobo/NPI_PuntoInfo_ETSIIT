package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.npi_puntoinfo_etsiit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.GrafoRepository
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import com.example.npi_puntoinfo_etsiit.formatearPlanta
import com.example.npi_puntoinfo_etsiit.normalizarPlanta
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.components.CompactReturnOutdoorsButton
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.ui.components.MapaRutaView
import com.example.npi_puntoinfo_etsiit.ui.components.esNodoActivoEnPlanta
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun NavigationScreen(
    destinationName: String? = null,
    destinationLocation: String? = null,
    isVerticalGuidanceActive: Boolean = false,
    onPostureChanged: (Boolean) -> Unit = {},
    onSelectDestination: (String, String) -> Unit = { _, _ -> },
    onClearRoute: () -> Unit = {},
    entryNodeId: String? = null,
    destinationNodeId: String? = null,
    onReturnOutdoors: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val profile = com.example.npi_puntoinfo_etsiit.data.UserSessionRepository.getProfile(context)
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

    val formattedTitle = remember(primaryTitle) {
        val trimmed = primaryTitle.trim()
        val dMatch = Regex("""^[Dd][_-](.+)""").find(trimmed)
        if (dMatch != null) {
            "Despacho ${dMatch.groupValues[1]}"
        } else {
            val aMatch = Regex("""^[Aa][_-](.+)""").find(trimmed)
            if (aMatch != null) {
                "Aula ${aMatch.groupValues[1]}"
            } else if (trimmed.equals("comedor_pss", ignoreCase = true)) {
                "Comedor Aynadamar"
            } else {
                trimmed
            }
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

    // Estimación del aula de origen según el horario del usuario (si está en clase o acaba de terminar)
    val estimatedClassroom = remember(context, activeDestination) {
        com.example.npi_puntoinfo_etsiit.data.ScheduleRepository.estimateCurrentOrRecentClassroom(context)
    }

    var userConfirmedNodeId by remember(activeDestination) {
        mutableStateOf<String?>(null)
    }

    var selectedEntryNodeId by remember(entryNodeId, userConfirmedNodeId) {
        mutableStateOf(userConfirmedNodeId ?: entryNodeId)
    }

    var showVerificationDialog by remember(activeDestination, userConfirmedNodeId) {
        mutableStateOf(hasActiveRoute && userConfirmedNodeId == null)
    }

    val nodoInicio = remember(grafo, selectedEntryNodeId) {
        val entryId = selectedEntryNodeId
        if (entryId != null) {
            grafo.obtenerNodoPorId(entryId)
                ?: grafo.buscarNodoPorDestino(entryId)
                ?: grafo.obtenerNodoInicioPredeterminado()
        } else {
            grafo.obtenerNodoInicioPredeterminado()
        }
    }

    val nodoDestino = remember(grafo, activeDestination, destinationNodeId, primaryTitle) {
        if (!hasActiveRoute) null
        else if (destinationNodeId != null) grafo.obtenerNodoPorId(destinationNodeId) ?: grafo.buscarNodoPorDestino(primaryTitle) ?: grafo.buscarNodoPorDestino(activeDestination)
        else grafo.buscarNodoPorDestino(primaryTitle) ?: grafo.buscarNodoPorDestino(activeDestination)
    }
    var isAccessibleRouteSelected by rememberSaveable(profile.isAccessibleRouting) {
        mutableStateOf(profile.isAccessibleRouting)
    }

    val isComedorToAula11 = remember(nodoInicio, nodoDestino, activeDestination) {
        val origName = nodoInicio?.nombre ?: ""
        val destName = nodoDestino?.nombre ?: activeDestination ?: ""
        (origName.contains("comedor", ignoreCase = true) || nodoInicio?.id?.contains("comedor", ignoreCase = true) == true) &&
                (destName.contains("1.1", ignoreCase = true) || destName.contains("A_11", ignoreCase = true) || nodoDestino?.id?.contains("A_11", ignoreCase = true) == true)
    }

    val rutaCalculada = remember(grafo, nodoInicio, nodoDestino, isAccessibleRouteSelected, isComedorToAula11) {
        if (hasActiveRoute) {
            if (isComedorToAula11 && isAccessibleRouteSelected) {
                grafo.obtenerRutaForzadaAscensor()
            } else if (nodoDestino != null && nodoInicio != null) {
                grafo.calcularRutaCompleta(nodoInicio.id, nodoDestino.id)
            } else null
        } else null
    }

    val currentOnPostureChanged by rememberUpdatedState(onPostureChanged)
    val currentOnSelectDestination by rememberUpdatedState(onSelectDestination)
    var isManualGuidanceLocked by rememberSaveable { mutableStateOf(false) }

    // Ciclo de vida y estado del recorrido interior basado en Pedómetro
    var indoorTripState by remember(rutaCalculada, profile.language, isAccessibleRouteSelected) {
        mutableStateOf(
            if (rutaCalculada != null) {
                com.example.npi_puntoinfo_etsiit.indoor.IndoorTripState.buildFromRoute(
                    "trip_${rutaCalculada.destino.id}",
                    rutaCalculada,
                    context,
                    isAccessibleRouteSelected
                )
            } else null
        )
    }

    // Pedómetro: monitorea pasos en interiores para avanzar automáticamente entre waypoints de la ruta
    DisposableEffect(context, hasActiveRoute, rutaCalculada) {
        val pedometerManager = com.example.npi_puntoinfo_etsiit.sensors.PedometerManager(context)

        if (hasActiveRoute && rutaCalculada != null) {
            pedometerManager.onStepDetected = {
                indoorTripState = indoorTripState?.onStepDetected()
            }
            pedometerManager.startListening()
        }

        onDispose {
            pedometerManager.stopListening()
        }
    }

    LaunchedEffect(isVerticalGuidanceActive) {
        if (!isVerticalGuidanceActive) {
            isManualGuidanceLocked = false
        }
    }

    // Acelerómetro: detecta cuando se levanta el móvil verticalmente para activar el guiado paso a paso,
    // y cuando se baja de nuevo para volver a mostrar el mapa (siempre que no se haya activado manualmente)
    DisposableEffect(context, hasActiveRoute, isManualGuidanceLocked) {
        val manager = AccelerometerManager(context)

        if (hasActiveRoute && !isManualGuidanceLocked) {
            manager.onPostureModeChanged = { isVertical ->
                triggerVibration(context, durationMs = 70)
                currentOnPostureChanged(isVertical)
            }

            manager.startListening()
        }

        onDispose {
            manager.stopListening()
            if (!isManualGuidanceLocked) {
                currentOnPostureChanged(false)
            }
        }
    }

    val todasLasPlantas = remember { listOf("PSS", "PB", "P1", "P2", "P3", "P4") }

    // Plantas donde el usuario tiene que caminar o transitar en la ruta
    val plantasEnRuta = remember(rutaCalculada) {
        if (rutaCalculada == null) {
            todasLasPlantas
        } else {
            val plantasConActividad = rutaCalculada.nodos.indices
                .filter { i -> esNodoActivoEnPlanta(rutaCalculada, i, normalizarPlanta(rutaCalculada.nodos[i].planta)) }
                .map { i -> normalizarPlanta(rutaCalculada.nodos[i].planta) }
                .distinct()

            if (plantasConActividad.isNotEmpty()) plantasConActividad else rutaCalculada.nodos.map { normalizarPlanta(it.planta) }.distinct()
        }
    }

    // Planta actualmente seleccionada en el visor
    var plantaSeleccionada by remember(rutaCalculada) {
        if (rutaCalculada == null) {
            mutableStateOf("PB")
        } else {
            val plantaOrigen = normalizarPlanta(rutaCalculada.origen.planta)
            mutableStateOf(
                if (plantasEnRuta.contains(plantaOrigen)) plantaOrigen else (plantasEnRuta.firstOrNull() ?: "PB")
            )
        }
    }

    // Lista de plantas disponibles para seleccionar:
    // Si la ruta abarca más de una planta, mostramos esas plantas en la ruta.
    // Si no hay ruta o es de 1 sola planta, mostramos todas las plantas para libre exploración.
    val plantasDisponibles = remember(plantasEnRuta) {
        if (plantasEnRuta.size > 1) plantasEnRuta else todasLasPlantas
    }

    LaunchedEffect(plantasDisponibles) {
        if (!plantasDisponibles.contains(plantaSeleccionada)) {
            plantaSeleccionada = plantasDisponibles.firstOrNull() ?: "PB"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // 1. EL MAPA INTERIOR A PANTALLA COMPLETA O GUIADO VERTICAL POR PEDÓMETRO
        if (hasActiveRoute && rutaCalculada != null && isVerticalGuidanceActive) {
            val currentTrip = indoorTripState
            if (currentTrip != null) {
                com.example.npi_puntoinfo_etsiit.ui.components.IndoorFullScreenStepGuidanceView(
                    tripState = currentTrip,
                    onStepSimulated = {
                        indoorTripState = indoorTripState?.onStepDetected()
                    },
                    onNextManual = {
                        indoorTripState = indoorTripState?.nextStepManual()
                    },
                    onPreviousManual = {
                        indoorTripState = indoorTripState?.previousStepManual()
                    },
                    onClose = {
                        isManualGuidanceLocked = false
                        onPostureChanged(false)
                    }
                )
            }
        } else {
            val bottomPadding = if (hasActiveRoute && rutaCalculada != null) {
                148.dp
            } else {
                24.dp
            }

            MapaRutaView(
                ruta = rutaCalculada,
                plantaSeleccionada = plantaSeleccionada,
                bottomPadding = bottomPadding,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Estado cargando si hay ruta activa pero todavía no se ha calculado
        if (hasActiveRoute && rutaCalculada == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
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
                        text = stringResource(R.string.calculating_route, primaryTitle),
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. CABECERA FLOTANTE SUPERIOR: Destino + Selector de Planta + Leyenda de colores
        if (!isVerticalGuidanceActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Píldora compacta con el destino (sin "Desde" ni planta redundante)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    color = SurfaceWhite.copy(alpha = 0.96f),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(UgrRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = UgrRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasActiveRoute) formattedTitle else stringResource(R.string.indoor_map_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            if (!hasActiveRoute) {
                                Text(
                                    text = stringResource(R.string.indoor_map_subtitle),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Selector de plantas y leyenda de colores en la parte superior
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f)),
                    color = SurfaceWhite.copy(alpha = 0.96f),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.floor_label),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            plantasDisponibles.forEach { p ->
                                val esActiva = (p == plantaSeleccionada)
                                val tieneRuta = plantasEnRuta.contains(p)
                                FilterChip(
                                    selected = esActiva,
                                    onClick = { plantaSeleccionada = p },
                                    label = {
                                        Text(
                                            text = formatearPlanta(p),
                                            fontSize = 11.sp,
                                            fontWeight = if (esActiva) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EtsiitBlue.copy(alpha = 0.14f),
                                        selectedLabelColor = EtsiitBlue
                                    ),
                                    leadingIcon = if (hasActiveRoute && tieneRuta && plantasDisponibles.size > plantasEnRuta.size) {
                                        {
                                            Box(
                                                Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (esActiva) EtsiitBlue else CampusTeal)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }

                        if (hasActiveRoute && rutaCalculada != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Text(stringResource(R.string.legend_start), fontSize = 11.sp, color = TextSecondary)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7C3AED)))
                                    Text(stringResource(R.string.legend_destination), fontSize = 11.sp, color = TextSecondary)
                                }
                                if (plantasEnRuta.size > 1) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                        Text(stringResource(R.string.legend_stairs_elevator), fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        } else if (!hasActiveRoute) {
                            Text(
                                text = stringResource(R.string.explore_floors_hint),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Fila para el botón "Volver al exterior" (alineado a la derecha, igual que "Ya estoy dentro" en GPS)
                if (onReturnOutdoors != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CompactReturnOutdoorsButton(
                            onClick = onReturnOutdoors
                        )
                    }
                }
            }
        }

        // 3. TARJETA INFERIOR FLOTANTE: Igual que en guiado exterior (distancia + gesto + botón rojo iniciar paso a paso)
        if (!isVerticalGuidanceActive && hasActiveRoute && rutaCalculada != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${rutaCalculada.distanciaMetros} m",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            // Selector para alternar entre Escaleras y Ascensor
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAccessibleRouteSelected) CampusTeal.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.06f),
                                modifier = Modifier.clickable {
                                    isAccessibleRouteSelected = !isAccessibleRouteSelected
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAccessibleRouteSelected) Icons.AutoMirrored.Filled.Accessible else Icons.AutoMirrored.Filled.DirectionsWalk,
                                        contentDescription = if (isAccessibleRouteSelected) "Ruta por ascensor" else "Ruta por escaleras",
                                        tint = if (isAccessibleRouteSelected) CampusTeal else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isAccessibleRouteSelected) "Ascensor" else "Escaleras",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAccessibleRouteSelected) CampusTeal else TextSecondary
                                    )
                                }
                            }
                        }

                        GestureBadge(
                            type = GestureType.LIFT_PHONE,
                            size = 38.dp,
                            iconSize = 20.dp,
                            onClick = {
                                isManualGuidanceLocked = true
                                onPostureChanged(true)
                            }
                        )
                    }

                    Button(
                        onClick = {
                            isManualGuidanceLocked = true
                            onPostureChanged(true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                    ) {
                        Text(
                            text = "Iniciar guiado paso a paso",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Diálogo emergente de verificación de ubicación inicial
        if (showVerificationDialog) {
            com.example.npi_puntoinfo_etsiit.ui.components.OriginVerificationDialog(
                grafo = grafo,
                estimatedClassroom = estimatedClassroom,
                destinationTitle = primaryTitle.ifBlank { activeDestination },
                onNodeConfirmed = { confirmedNode ->
                    userConfirmedNodeId = confirmedNode.id
                    selectedEntryNodeId = confirmedNode.id
                    showVerificationDialog = false
                },
                onDismiss = {
                    val fallbackId = selectedEntryNodeId ?: grafo.obtenerNodoInicioPredeterminado().id
                    userConfirmedNodeId = fallbackId
                    selectedEntryNodeId = fallbackId
                    showVerificationDialog = false
                }
            )
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

