package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.R
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import com.example.npi_puntoinfo_etsiit.formatearPlanta
import com.example.npi_puntoinfo_etsiit.normalizarPlanta
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
 * Determina si un nodo de la ruta debe representarse visualmente en la planta indicada.
 * Se excluyen nodos intermedios de paso vertical (como un ascensor o hueco de escalera por el
 * que la ruta pasa entre dos plantas distintas sin que el usuario camine ni se detenga en la planta actual).
 */
internal fun esNodoActivoEnPlanta(ruta: RutaCalculada, index: Int, plantaNorm: String): Boolean {
    val nodo = ruta.nodos[index]
    if (normalizarPlanta(nodo.planta) != plantaNorm) return false
    if (nodo.id == ruta.origen.id || nodo.id == ruta.destino.id) return true

    val vecinoAnteriorMismaPlanta = index > 0 && normalizarPlanta(ruta.nodos[index - 1].planta) == plantaNorm
    val vecinoSiguienteMismaPlanta = index < ruta.nodos.size - 1 && normalizarPlanta(ruta.nodos[index + 1].planta) == plantaNorm

    return vecinoAnteriorMismaPlanta || vecinoSiguienteMismaPlanta
}

/**
 * Componente interactivo que dibuja el plano oficial de la ETSIIT y traza encima la ruta calculada por Dijkstra.
 * Permite hacer zoom con pellizco (pinch-to-zoom), arrastrar para explorar y cambiar de planta si la ruta
 * pasa por varias plantas del edificio.
 */
@Composable
fun MapaRutaView(
    ruta: RutaCalculada? = null,
    plantaSeleccionada: String = "PB",
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    // Nodos de la ruta que pertenecen a la planta seleccionada y en los que el usuario camina o transita
    val nodosPlantaActual = remember(ruta, plantaSeleccionada) {
        if (ruta == null) emptyList()
        else {
            val plantaNorm = normalizarPlanta(plantaSeleccionada)
            ruta.nodos.filterIndexed { index, _ ->
                esNodoActivoEnPlanta(ruta, index, plantaNorm)
            }
        }
    }

    // Recurso del plano según la planta
    val planoResId = remember(plantaSeleccionada) {
        when (normalizarPlanta(plantaSeleccionada)) {
            "PSS", "P-1", "-1" -> R.drawable.mapa_pss
            "PB", "B", "0" -> R.drawable.mapa_pb
            "P1", "1" -> R.drawable.mapa_p1
            "P2", "2" -> R.drawable.mapa_p2
            "P3", "3" -> R.drawable.mapa_p3
            "P4", "4" -> R.drawable.mapa_p4
            else -> R.drawable.mapa_pb
        }
    }

    // Control de gestos de zoom y desplazamiento panorámico
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Contenedor del plano interactivo a pantalla completa
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceWhite)
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
                    val plantaNorm = normalizarPlanta(plantaSeleccionada)

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
                        if (esNodoActivoEnPlanta(r, i, plantaNorm)) {
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

                    // Dibujar puntos intermedios en los pasillos y conectores de cambio de planta
                    for (i in r.nodos.indices) {
                        if (!esNodoActivoEnPlanta(r, i, plantaNorm)) continue

                        val nodo = r.nodos[i]
                        val pt = mapPoint(nodo)
                        val esInicio = (nodo.id == r.origen.id)
                        val esFin = (nodo.id == r.destino.id)

                        // Es cambio de planta si en la ruta el nodo anterior o siguiente pertenece a otra planta
                        val esCambioPlanta = (i > 0 && normalizarPlanta(r.nodos[i - 1].planta) != plantaNorm) ||
                            (i < r.nodos.size - 1 && normalizarPlanta(r.nodos[i + 1].planta) != plantaNorm)

                        if (!esInicio && !esFin && !esCambioPlanta) {
                            // Punto intermedio normal de pasillo
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
                        } else if (esCambioPlanta && !esInicio && !esFin) {
                            // Conector de cambio de planta (escaleras o ascensor en el extremo del tramo)
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
                    if (normalizarPlanta(r.origen.planta) == plantaNorm) {
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
                    if (normalizarPlanta(r.destino.planta) == plantaNorm) {
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

        // Controles de zoom táctil flotantes sobre el plano
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceWhite.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f)),
                onClick = { scale = (scale + 0.6f).coerceAtMost(4.5f) },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Acercar",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = SurfaceWhite.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f)),
                onClick = {
                    scale = (scale - 0.6f).coerceAtLeast(1f)
                    if (scale == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Alejar",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (scale > 1f || offsetX != 0f || offsetY != 0f) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceWhite.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f)),
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Restablecer vista",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
