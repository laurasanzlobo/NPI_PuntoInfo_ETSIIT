package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.R
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import com.example.npi_puntoinfo_etsiit.formatearPlanta
import com.example.npi_puntoinfo_etsiit.ui.theme.*

// Dimensiones de resolución nativa de los planos originales de la ETSIIT (1131 x 1600 px)
private const val PLANO_ANCHO_ORIGINAL = 1131f
private const val PLANO_ALTO_ORIGINAL = 1600f

// Colores de alto contraste sobre el plano arquitectónico en líneas rojas
private val ColorRutaOutline = Color.White
private val ColorRutaLinea = Color(0xFF2563EB) // Azul intenso
private val ColorOrigen = Color(0xFF10B981)    // Verde esmeralda
private val ColorDestino = Color(0xFF7C3AED)   // Morado vibrante (evita el rojo del plano)
private val ColorEscaleras = Color(0xFFF59E0B) // Ámbar dorado para transición entre plantas

/**
 * Componente interactivo que dibuja el plano oficial de la ETSIIT y traza encima la ruta calculada por Dijkstra.
 * Permite hacer zoom con pellizco (pinch-to-zoom), arrastrar para explorar y cambiar de planta si la ruta
 * pasa por varias plantas del edificio.
 */
@Composable
fun MapaRutaView(
    ruta: RutaCalculada? = null,
    modifier: Modifier = Modifier
) {
    // Lista de todas las plantas para el modo de exploración libre (sin ruta activa)
    val todasLasPlantas = remember { listOf("PSS", "PB", "P1", "P2", "P3", "P4") }

    // Lista ordenada de plantas involucradas en la ruta calculada.
    // Si no hay ruta, se ofrecen todas las plantas del campus para navegar libremente.
    // Si hay ruta, se filtran plantas intermedias con 1 solo nodo (escalera de paso).
    val plantasEnRuta = remember(ruta) {
        if (ruta == null) {
            todasLasPlantas
        } else {
            val nodosPorPlanta = ruta.nodos.groupBy { it.planta.trim().uppercase() }
            val origenPlanta = ruta.origen.planta.trim().uppercase()
            val destinoPlanta = ruta.destino.planta.trim().uppercase()

            val plantasFiltradas = ruta.nodos
                .map { it.planta.trim().uppercase() }
                .distinct()
                .filter { planta ->
                    planta == origenPlanta || planta == destinoPlanta || (nodosPorPlanta[planta]?.size ?: 0) > 1
                }

            if (plantasFiltradas.isNotEmpty()) plantasFiltradas else ruta.nodos.map { it.planta.trim().uppercase() }.distinct()
        }
    }

    // Planta actualmente visible en el mapa: por defecto la planta de origen donde se encuentra el usuario (ej. Planta Baja)
    var plantaSeleccionada by remember(ruta) {
        if (ruta == null) {
            mutableStateOf("PB")
        } else {
            val plantaOrigen = ruta.origen.planta.trim().uppercase()
            mutableStateOf(
                if (plantasEnRuta.contains(plantaOrigen)) plantaOrigen else (plantasEnRuta.firstOrNull() ?: "PB")
            )
        }
    }

    // Nodos de la ruta que pertenecen a la planta seleccionada
    val nodosPlantaActual = remember(ruta, plantaSeleccionada) {
        ruta?.nodos?.filter { it.planta.trim().uppercase() == plantaSeleccionada } ?: emptyList()
    }

    // Recurso del plano según la planta
    val planoResId = remember(plantaSeleccionada) {
        when (plantaSeleccionada) {
            "P-1", "-1", "PSS" -> R.drawable.mapa_pss
            "B", "0", "PB" -> R.drawable.mapa_pb
            "1", "P1" -> R.drawable.mapa_p1
            "2", "P2" -> R.drawable.mapa_p2
            "3", "P3" -> R.drawable.mapa_p3
            "4", "P4" -> R.drawable.mapa_p4
            else -> R.drawable.mapa_pb
        }
    }

    // Control de gestos de zoom y desplazamiento panorámico
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Selector de planta si la ruta abarca más de una planta (con scroll horizontal para que nunca se corte)
        if (plantasEnRuta.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
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
                plantasEnRuta.forEach { p ->
                    val esActiva = (p == plantaSeleccionada)
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
                        )
                    )
                }
            }
        }

        // Contenedor del plano interactivo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceWhite)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4.5f)
                            if (scale > 1f) {
                                val maxPanX = (PLANO_ANCHO_ORIGINAL * (scale - 1f)) / 4f
                                val maxPanY = (PLANO_ALTO_ORIGINAL * (scale - 1f)) / 4f
                                offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            ) {
                // 1. Imagen del plano de fondo
                Image(
                    painter = painterResource(id = planoResId),
                    contentDescription = "Plano ${formatearPlanta(plantaSeleccionada)}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // 2. Capa Canvas para trazar la ruta de Dijkstra sobre el plano (solo si hay ruta activa)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = ruta ?: return@Canvas
                    if (nodosPlantaActual.isEmpty()) return@Canvas

                    // Calcular la relación de escala de ContentScale.Fit dentro del Canvas
                    val scaleFactor = minOf(
                        size.width / PLANO_ANCHO_ORIGINAL,
                        size.height / PLANO_ALTO_ORIGINAL
                    )
                    val displayedWidth = PLANO_ANCHO_ORIGINAL * scaleFactor
                    val displayedHeight = PLANO_ALTO_ORIGINAL * scaleFactor
                    val leftOffset = (size.width - displayedWidth) / 2f
                    val topOffset = (size.height - displayedHeight) / 2f

                    fun mapPoint(nodo: Nodo): Offset {
                        return Offset(
                            x = leftOffset + (nodo.x * scaleFactor),
                            y = topOffset + (nodo.y * scaleFactor)
                        )
                    }

                    // Trazar las líneas de los tramos que pertenecen a esta planta
                    val puntosPlanta = mutableListOf<List<Offset>>()
                    var tramoActual = mutableListOf<Offset>()

                    for (i in r.nodos.indices) {
                        val n = r.nodos[i]
                        if (n.planta.trim().uppercase() == plantaSeleccionada) {
                            tramoActual.add(mapPoint(n))
                        } else {
                            if (tramoActual.size >= 2) {
                                puntosPlanta.add(tramoActual.toList())
                            }
                            tramoActual = mutableListOf()
                        }
                    }
                    if (tramoActual.size >= 2) {
                        puntosPlanta.add(tramoActual.toList())
                    }

                    // Dibujar el trazado continuo de la ruta
                    puntosPlanta.forEach { tramo ->
                        val path = Path().apply {
                            moveTo(tramo.first().x, tramo.first().y)
                            for (j in 1 until tramo.size) {
                                lineTo(tramo[j].x, tramo[j].y)
                            }
                        }

                        // Línea exterior blanca para garantizar contraste total contra las paredes rojas
                        drawPath(
                            path = path,
                            color = ColorRutaOutline,
                            style = Stroke(
                                width = 11.dp.toPx() / scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Línea interior azul intenso de la ruta
                        drawPath(
                            path = path,
                            color = ColorRutaLinea,
                            style = Stroke(
                                width = 6.dp.toPx() / scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Dibujar puntos intermedios en los pasillos
                    nodosPlantaActual.forEach { nodo ->
                        val pt = mapPoint(nodo)
                        val esInicio = (nodo.id == r.origen.id)
                        val esFin = (nodo.id == r.destino.id)
                        val esConectorEscaleras = nodo.nombre.contains("Escalera", ignoreCase = true) ||
                            nodo.nombre.contains("Ascensor", ignoreCase = true)

                        if (!esInicio && !esFin && !esConectorEscaleras) {
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx() / scale,
                                center = pt
                            )
                            drawCircle(
                                color = ColorRutaLinea,
                                radius = 3.dp.toPx() / scale,
                                center = pt
                            )
                        } else if (esConectorEscaleras && !esInicio && !esFin) {
                            drawCircle(
                                color = Color.White,
                                radius = 7.dp.toPx() / scale,
                                center = pt
                            )
                            drawCircle(
                                color = ColorEscaleras,
                                radius = 5.dp.toPx() / scale,
                                center = pt
                            )
                        }
                    }

                    // Punto de partida (Verde esmeralda con aro blanco) si está en esta planta
                    if (r.origen.planta.trim().uppercase() == plantaSeleccionada) {
                        val ptInicio = mapPoint(r.origen)
                        drawCircle(
                            color = Color.White,
                            radius = 11.dp.toPx() / scale,
                            center = ptInicio
                        )
                        drawCircle(
                            color = ColorOrigen,
                            radius = 8.5.dp.toPx() / scale,
                            center = ptInicio
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx() / scale,
                            center = ptInicio
                        )
                    }

                    // Punto de destino (Morado vibrante con aro blanco, sin nada de rojo) si está en esta planta
                    if (r.destino.planta.trim().uppercase() == plantaSeleccionada) {
                        val ptDestino = mapPoint(r.destino)
                        // Aro blanco exterior
                        drawCircle(
                            color = Color.White,
                            radius = 13.dp.toPx() / scale,
                            center = ptDestino
                        )
                        // Círculo morado llamativo
                        drawCircle(
                            color = ColorDestino,
                            radius = 10.dp.toPx() / scale,
                            center = ptDestino
                        )
                        // Diana blanca central
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx() / scale,
                            center = ptDestino
                        )
                    }
                }
            }

            // Controles de zoom táctil en la esquina inferior derecha
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceWhite.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    onClick = { scale = (scale + 0.6f).coerceAtMost(4.5f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Acercar",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = SurfaceWhite.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    onClick = {
                        scale = (scale - 0.6f).coerceAtLeast(1f)
                        if (scale == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Alejar",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (scale > 1f || offsetX != 0f || offsetY != 0f) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceWhite.copy(alpha = 0.92f),
                        shadowElevation = 3.dp,
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Restablecer vista",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Leyenda sutil en la esquina inferior izquierda (solo cuando hay ruta activa)
            if (ruta != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceWhite.copy(alpha = 0.90f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ColorOrigen)
                            )
                            Text("Inicio", fontSize = 10.sp, color = TextSecondary)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ColorDestino)
                            )
                            Text("Destino", fontSize = 10.sp, color = TextSecondary)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ColorEscaleras)
                            )
                            Text("Cambio de planta", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
