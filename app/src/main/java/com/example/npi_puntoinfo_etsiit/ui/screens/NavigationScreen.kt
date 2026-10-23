package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val nodoInicio = remember(grafo, entryNodeId) {
        if (entryNodeId != null) grafo.obtenerNodoPorId(entryNodeId) else grafo.obtenerNodoInicioPredeterminado()
    }
    val nodoDestino = remember(grafo, activeDestination, destinationNodeId) {
        if (!hasActiveRoute) null else if (destinationNodeId != null) grafo.obtenerNodoPorId(destinationNodeId)
        else grafo.buscarNodoPorDestino(activeDestination)
    }
    val rutaCalculada = remember(grafo, nodoInicio, nodoDestino) {
        if (nodoDestino != null && nodoInicio != null) {
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
        // 1. EL MAPA INTERIOR A PANTALLA COMPLETA O GUIADO VERTICAL
        if (hasActiveRoute && rutaCalculada != null && isVerticalGuidanceActive) {
            // Modo guiado vertical paso a paso completo
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 90.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EtsiitBlue.copy(alpha = 0.12f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = EtsiitBlue,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Text(
                        text = "Guiado Paso a Paso (Dijkstra)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )

                    Text(
                        text = "Vas desde ${rutaCalculada.origen.nombre.replace('_', ' ')} hasta ${rutaCalculada.destino.nombre.replace('_', ' ')} y recorres ${rutaCalculada.nodos.size} nodos (${rutaCalculada.distanciaMetros} metros · ${rutaCalculada.tiempoEstimadoFormateado}).",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BackgroundLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Secuencia de paso",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CampusTeal.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${rutaCalculada.nodos.size} nodos",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CampusTeal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = rutaCalculada.nodos.joinToString(" → ") { it.nombre.replace('_', ' ') },
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        } else {
            val bottomPadding = if (hasActiveRoute && rutaCalculada != null) {
                128.dp
            } else {
                98.dp
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
                        text = "Calculando ruta a $primaryTitle...",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. CABECERA FLOTANTE SUPERIOR (Estilo GPS / OutdoorNavigationScreen)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Píldora ovalada con el destino / título
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                color = SurfaceWhite.copy(alpha = 0.96f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasActiveRoute) formattedTitle else "Mapa ETSIIT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            if (hasActiveRoute && subtitleText.isNotBlank() && subtitleText != "Ruta activa") {
                                Text(
                                    text = subtitleText,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            } else if (!hasActiveRoute) {
                                Text(
                                    text = "Plano interactivo del edificio",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (hasActiveRoute) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Loguito del gesto del acelerómetro (flecha hacia arriba / levantar el móvil)
                            GestureBadge(
                                type = GestureType.LIFT_PHONE,
                                size = 28.dp,
                                iconSize = 16.dp,
                                onClick = { onPostureChanged(!isVerticalGuidanceActive) }
                            )

                            // Botón para finalizar / cancelar la ruta activa
                            IconButton(
                                onClick = onClearRoute,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar ruta",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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

        // 3. TARJETA INFERIOR FLOTANTE (Solo cuando no está activo el modo vertical completo)
        if (!isVerticalGuidanceActive) {
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
                        .padding(14.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Selector de plantas con scroll horizontal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Planta:",
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

                    // Si hay ruta activa: leyenda a la izquierda y distancia en metros a la derecha
                    if (hasActiveRoute && rutaCalculada != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leyenda: Inicio, Destino, Escalera / Ascensor
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Text("Inicio", fontSize = 11.sp, color = TextSecondary)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7C3AED)))
                                    Text("Destino", fontSize = 11.sp, color = TextSecondary)
                                }
                                if (plantasEnRuta.size > 1) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                        Text("Escalera / Ascensor", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }

                            // Alineado a la derecha del todo: solo los metros
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EtsiitBlue.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = "${rutaCalculada.distanciaMetros} m",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EtsiitBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else if (!hasActiveRoute) {
                        Text(
                            text = "Toca una planta para explorar su distribución arquitectónica",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
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

