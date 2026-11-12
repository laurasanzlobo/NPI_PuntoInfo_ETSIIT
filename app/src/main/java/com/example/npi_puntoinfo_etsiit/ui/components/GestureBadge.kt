package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.example.npi_puntoinfo_etsiit.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.theme.*

/**
 * Icono personalizado para el gesto «¡Llévame!» (Inclinar a la izquierda y volver):
 * Representa un móvil vertical con una flecha curva hacia la izquierda.
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
 * Icono personalizado para el Gesto de Acción (Inclinar hacia ti):
 * Muestra el móvil a la izquierda y una flecha recta vertical hacia abajo (↓) a la derecha,
 * dejando el móvil visible y comunicando de forma directa y limpia el movimiento hacia abajo / hacia ti.
 */
val TiltTowardsUserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TiltTowardsUserIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        // Contorno exterior del móvil (lado izquierdo)
        pathData = listOf(
            PathNode.MoveTo(4.5f, 3.5f),
            PathNode.HorizontalTo(12.5f),
            PathNode.CurveTo(13.6f, 3.5f, 14.5f, 4.4f, 14.5f, 5.5f),
            PathNode.VerticalTo(18.5f),
            PathNode.CurveTo(14.5f, 19.6f, 13.6f, 20.5f, 12.5f, 20.5f),
            PathNode.HorizontalTo(4.5f),
            PathNode.CurveTo(3.4f, 20.5f, 2.5f, 19.6f, 2.5f, 18.5f),
            PathNode.VerticalTo(5.5f),
            PathNode.CurveTo(2.5f, 4.4f, 3.4f, 3.5f, 4.5f, 3.5f),
            PathNode.Close
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).addPath(
        // Altavoz superior del móvil
        pathData = listOf(
            PathNode.MoveTo(6.8f, 5.5f),
            PathNode.HorizontalTo(10.2f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round
    ).addPath(
        // Barra inferior de navegación
        pathData = listOf(
            PathNode.MoveTo(6.5f, 18.5f),
            PathNode.HorizontalTo(10.5f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round
    ).addPath(
        // Línea vertical recta descendente de la flecha (lado derecho)
        pathData = listOf(
            PathNode.MoveTo(19.0f, 4.5f),
            PathNode.LineTo(19.0f, 15.0f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round
    ).addPath(
        // Punta de flecha hacia abajo
        pathData = listOf(
            PathNode.MoveTo(19.0f, 20.0f),
            PathNode.LineTo(15.2f, 14.0f),
            PathNode.LineTo(22.8f, 14.0f),
            PathNode.Close
        ),
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.6f,
        strokeLineJoin = StrokeJoin.Round
    ).build()
}

/**
 * Icono personalizado para el Gesto de Zoom (Pellizcar con dos dedos):
 * Representa cuatro corchetes angulares de expansión y zoom.
 */
val PinchZoomIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PinchZoomIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = listOf(
            // Esquina superior izquierda
            PathNode.MoveTo(4f, 9f), PathNode.LineTo(4f, 4f), PathNode.LineTo(9f, 4f),
            // Esquina superior derecha
            PathNode.MoveTo(15f, 4f), PathNode.LineTo(20f, 4f), PathNode.LineTo(20f, 9f),
            // Esquina inferior derecha
            PathNode.MoveTo(20f, 15f), PathNode.LineTo(20f, 20f), PathNode.LineTo(15f, 20f),
            // Esquina inferior izquierda
            PathNode.MoveTo(9f, 20f), PathNode.LineTo(4f, 20f), PathNode.LineTo(4f, 15f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()
}

/**
 * Tipos de gestos físicos soportados en la aplicación Punto Info ETSIIT.
 */
enum class GestureType(
    @StringRes val titleRes: Int,
    @StringRes val gestureNameRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val howToRes: Int,
    @StringRes val whatForRes: Int,
    val icon: ImageVector,
    val badgeColor: Color
) {
    TAKE_ME_THERE(
        titleRes = R.string.gesture_take_me_there_title,
        gestureNameRes = R.string.gesture_take_me_there_name,
        descriptionRes = R.string.gesture_take_me_there_desc,
        howToRes = R.string.gesture_take_me_there_howto,
        whatForRes = R.string.gesture_take_me_there_whatfor,
        icon = WristTwistIcon,
        badgeColor = CampusTeal
    ),
    TRIPLE_SHAKE(
        titleRes = R.string.gesture_triple_shake_title,
        gestureNameRes = R.string.gesture_triple_shake_name,
        descriptionRes = R.string.gesture_triple_shake_desc,
        howToRes = R.string.gesture_triple_shake_howto,
        whatForRes = R.string.gesture_triple_shake_whatfor,
        icon = Icons.Default.Vibration,
        badgeColor = UgrRed
    ),
    ACTION_TRIGGER(
        titleRes = R.string.gesture_action_trigger_title,
        gestureNameRes = R.string.gesture_action_trigger_name,
        descriptionRes = R.string.gesture_action_trigger_desc,
        howToRes = R.string.gesture_action_trigger_howto,
        whatForRes = R.string.gesture_action_trigger_whatfor,
        icon = TiltTowardsUserIcon,
        badgeColor = WarningAmber
    ),
    LIFT_PHONE(
        titleRes = R.string.gesture_lift_phone_title,
        gestureNameRes = R.string.gesture_lift_phone_name,
        descriptionRes = R.string.gesture_lift_phone_desc,
        howToRes = R.string.gesture_lift_phone_howto,
        whatForRes = R.string.gesture_lift_phone_whatfor,
        icon = Icons.Default.ArrowUpward,
        badgeColor = EtsiitBlue
    ),
    PROXIMITY_VOICE(
        titleRes = R.string.gesture_proximity_voice_title,
        gestureNameRes = R.string.gesture_proximity_voice_name,
        descriptionRes = R.string.gesture_proximity_voice_desc,
        howToRes = R.string.gesture_proximity_voice_howto,
        whatForRes = R.string.gesture_proximity_voice_whatfor,
        icon = Icons.Default.Mic,
        badgeColor = VoicePurple
    ),
    PINCH_ZOOM(
        titleRes = R.string.gesture_pinch_zoom_title,
        gestureNameRes = R.string.gesture_pinch_zoom_name,
        descriptionRes = R.string.gesture_pinch_zoom_desc,
        howToRes = R.string.gesture_pinch_zoom_howto,
        whatForRes = R.string.gesture_pinch_zoom_whatfor,
        icon = PinchZoomIcon,
        badgeColor = Color(0xFF4F46E5)
    );

    val title: String @Composable get() = stringResource(titleRes)
    val gestureName: String @Composable get() = stringResource(gestureNameRes)
    val description: String @Composable get() = stringResource(descriptionRes)
    val howTo: String @Composable get() = stringResource(howToRes)
    val whatFor: String @Composable get() = stringResource(whatForRes)
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
    containerColor: Color? = null,
    contentColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    val bg = containerColor ?: type.badgeColor.copy(alpha = 0.12f)
    val tint = contentColor ?: type.badgeColor
    val borderModifier = if (containerColor != null) Modifier else Modifier.border(1.dp, type.badgeColor.copy(alpha = 0.35f), CircleShape)

    val isInteractive = onClick != null || showTooltipOnClick
    val clickModifier = if (isInteractive) {
        Modifier.clickable {
            if (onClick != null) {
                onClick()
            } else if (showTooltipOnClick) {
                showDialog = true
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(borderModifier)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = "${type.title}: ${type.gestureName}",
            tint = tint,
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
                    text = stringResource(R.string.gesture_name_label, type.gestureName),
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
                    text = stringResource(R.string.gesture_how_to_label),
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
                    text = stringResource(R.string.gesture_what_for_label),
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UgrRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = stringResource(R.string.understood),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    )
}

/**
 * Botón con la «i» de información sobre los gestos del acelerómetro.
 * Muestra un circulito gris sutil y al pulsarlo abre la pantalla de guía de gestos.
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
            contentDescription = "Guía de gestos",
            tint = TextSecondary,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Pantalla completa (pestaña sin scroll vertical) con la Guía de Gestos Físicos.
 * Todos los gestos se distribuyen equitativamente en la pantalla mediante Modifier.weight(1f)
 * para que quepan siempre en una sola vista sin necesidad de deslizar hacia abajo.
 */
@Composable
fun GesturesGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tarjetas de los 5 gestos repartidas equitativamente sin scroll
        GestureType.entries.forEach { gesture ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icono circular grande a la izquierda
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(gesture.badgeColor.copy(alpha = 0.14f))
                            .border(1.dp, gesture.badgeColor.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = gesture.icon,
                            contentDescription = gesture.title,
                            tint = gesture.badgeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Textos breves y directos a la derecha
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = gesture.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = gesture.badgeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = gesture.gestureName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = gesture.badgeColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Frase directa de cómo realizar el gesto (sin "Cómo:")
                        Text(
                            text = gesture.howTo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Para qué sirve (con "Uso:")
                        Text(
                            text = stringResource(R.string.gesture_usage_label, gesture.whatFor),
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
