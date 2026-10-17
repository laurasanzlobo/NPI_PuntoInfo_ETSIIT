package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.theme.*

/**
 * Icono personalizado para el gesto de giro de muñeca («¡Llévame!»):
 * Representa un móvil vertical con una flecha que sale de la derecha,
 * cruza por delante de la pantalla y rota hacia la izquierda.
 */
val WristTwistIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WristTwistIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(9.4f, 3.5f),
            PathNode.HorizontalTo(14.6f),
            PathNode.CurveTo(15.8f, 3.5f, 16.8f, 4.5f, 16.8f, 5.7f),
            PathNode.VerticalTo(18.3f),
            PathNode.CurveTo(16.8f, 19.5f, 15.8f, 20.5f, 14.6f, 20.5f),
            PathNode.HorizontalTo(9.4f),
            PathNode.CurveTo(8.2f, 20.5f, 7.2f, 19.5f, 7.2f, 18.3f),
            PathNode.VerticalTo(5.7f),
            PathNode.CurveTo(7.2f, 4.5f, 8.2f, 3.5f, 9.4f, 3.5f),
            PathNode.Close
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(11.0f, 5.5f),
            PathNode.HorizontalTo(13.0f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(10.5f, 18.5f),
            PathNode.HorizontalTo(13.5f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(17.0f, 16.0f),
            PathNode.CurveTo(19.5f, 14.5f, 20.5f, 12.0f, 19.5f, 10.0f),
            PathNode.CurveTo(17.5f, 8.0f, 14.5f, 7.5f, 11.5f, 7.5f),
            PathNode.CurveTo(8.5f, 7.5f, 6.2f, 9.5f, 5.0f, 13.5f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(2.5f, 16.0f),
            PathNode.LineTo(3.2f, 11.5f),
            PathNode.LineTo(7.0f, 15.5f),
            PathNode.Close
        ),
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.6f,
        strokeLineJoin = StrokeJoin.Round
    ).build()
}

/**
 * Tipos de gestos físicos soportados en la aplicación Punto Info ETSIIT.
 */
enum class GestureType(
    val title: String,
    val gestureName: String,
    val description: String,
    val howTo: String,
    val whatFor: String,
    val icon: ImageVector,
    val badgeColor: Color
) {
    TAKE_ME_THERE(
        title = "Gesto «¡Llévame!»",
        gestureName = "Giro de muñeca",
        description = "Gira el terminal verticalmente hacia la izquierda con un giro de muñeca para iniciar el guiado y ver la ruta hasta este destino.",
        howTo = "Gira el móvil de pie hacia la izquierda con un giro rápido de muñeca (o pulsa su icono en pantalla).",
        whatFor = "Inicia el mapa y traza automáticamente la ruta guiada hacia el destino (comedor, despacho o aula).",
        icon = WristTwistIcon,
        badgeColor = CampusTeal
    ),
    ACTION_TRIGGER(
        title = "Gesto de Acción",
        gestureName = "Movimiento físico de acción",
        description = "Realiza el gesto de acción rápida para encargar comida para llevar o enviar un correo al profesor.",
        howTo = "Realiza un movimiento rápido de inclinación/toque hacia el frente (o pulsa su icono en pantalla).",
        whatFor = "Ejecuta al instante la acción principal de la tarjeta: encargar comida para llevar en comedores o redactar un correo al profesor.",
        icon = Icons.Default.TouchApp,
        badgeColor = WarningAmber
    ),
    LIFT_PHONE(
        title = "Guiado Paso a Paso",
        gestureName = "Levantar el terminal",
        description = "Levanta el móvil verticalmente para ver el guiado paso a paso con fotos del recorrido, o bájalo horizontalmente para volver al mapa 2D.",
        howTo = "Levanta el terminal a posición vertical frente a ti; bájalo a posición horizontal para volver al plano 2D.",
        whatFor = "Alterna durante una ruta entre la vista de plano general 2D y el guiado inmersivo con fotografías reales de cada pasillo de la ETSIIT.",
        icon = Icons.Default.ArrowUpward,
        badgeColor = EtsiitBlue
    ),
    TRIPLE_SHAKE(
        title = "Consulta de Próxima Clase",
        gestureName = "Agitar 3 veces",
        description = "Agita el teléfono 3 veces consecutivas en cualquier pantalla de la app para consultar tu clase actual o próxima y su aula correspondiente.",
        howTo = "Agita el móvil con energía de un lado a otro 3 veces seguidas (o pulsa el circulito rojo de la cabecera).",
        whatFor = "Abre una ventana emergente en cualquier pantalla mostrando la clase que tienes ahora o la siguiente, el horario, el aula y acceso directo para guiarte.",
        icon = Icons.Default.Vibration,
        badgeColor = UgrRed
    )
}

/**
 * Loguito o distintivo visual para indicar la disponibilidad de un gesto físico en la pantalla.
 * Al pulsar sobre él, se muestra una ventana explicativa con la descripción del gesto.
 */
@Composable
fun GestureBadge(
    type: GestureType,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    iconSize: Dp = 17.dp,
    showTooltipOnClick: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(type.badgeColor.copy(alpha = 0.12f))
            .border(1.dp, type.badgeColor.copy(alpha = 0.35f), CircleShape)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else if (showTooltipOnClick) {
                    showDialog = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = "${type.title}: ${type.gestureName}",
            tint = type.badgeColor,
            modifier = Modifier.size(iconSize)
        )
    }

    if (showDialog) {
        GestureExplanationDialog(
            type = type,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Chip con icono y etiqueta de texto para lugares donde se dispone de espacio horizontal.
 */
@Composable
fun GestureChip(
    type: GestureType,
    modifier: Modifier = Modifier,
    label: String = type.gestureName,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            if (onClick != null) {
                onClick()
            } else {
                showDialog = true
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = type.badgeColor.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, type.badgeColor.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = type.badgeColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = type.badgeColor
            )
        }
    }

    if (showDialog) {
        GestureExplanationDialog(
            type = type,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Diálogo emergente que explica de forma clara y accesible cómo funciona el gesto pulsado.
 */
@Composable
fun GestureExplanationDialog(
    type: GestureType,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        iconContentColor = type.badgeColor,
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(type.badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = type.badgeColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = type.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Gesto: ${type.gestureName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = type.badgeColor
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = type.description,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "• Cómo es el gesto:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = TextPrimary
                )
                Text(
                    text = type.howTo,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Text(
                    text = "• Para qué sirve:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = TextPrimary
                )
                Text(
                    text = type.whatFor,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Botón con la «i» de información sobre los gestos del acelerómetro.
 * Muestra un circulito gris sutil y al pulsarlo abre la guía completa de gestos.
 */
@Composable
fun GestureGuideButton(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(TextSecondary.copy(alpha = 0.12f))
            .border(1.dp, TextSecondary.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Guía de gestos del acelerómetro",
            tint = TextSecondary,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Diálogo emergente que lista todas las funcionalidades de gestos del acelerómetro de la app.
 * A la izquierda muestra el loguito correspondiente y a la derecha cómo realizar el gesto y para qué sirve.
 */
@Composable
fun AccelerometerGesturesGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(TextSecondary.copy(alpha = 0.12f))
                        .border(1.dp, TextSecondary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Gestos del Acelerómetro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Funciones por movimiento del móvil",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "La aplicación utiliza el acelerómetro del dispositivo para ofrecer atajos y acciones físicas rápidas mediante gestos:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.7f))

                GestureType.values().forEach { gesture ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Loguito correspondiente a la izquierda
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(gesture.badgeColor.copy(alpha = 0.15f))
                                .border(1.dp, gesture.badgeColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = gesture.icon,
                                contentDescription = null,
                                tint = gesture.badgeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Explicación de cómo es y para qué sirve a la derecha
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = gesture.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Gesto: ${gesture.gestureName}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = gesture.badgeColor
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "• Cómo es el gesto:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = gesture.howTo,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "• Para qué sirve:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = gesture.whatFor,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (gesture != GestureType.values().last()) {
                        HorizontalDivider(
                            color = SurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Entendido",
                    color = UgrRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    )
}

