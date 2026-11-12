package com.example.npi_puntoinfo_etsiit.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.sensors.ProximityManager
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import kotlin.math.abs

/**
 * Identificadores de las zonas de la interfaz que el tutorial ilumina con Spotlight (Coach Marks).
 */
enum class SpotlightTargetId {
    PROFILE_ICON,
    GESTURE_BADGES,
    HOME_NEXT_CLASS,
    HOME_NEWS,
    HOME_SCHEDULE_BUTTON,
    HOME_SERVICES_BUTTON,
    HOME_DINING_BUTTON
}

/**
 * Estado reactivo compartido donde las pantallas registran las coordenadas reales (`boundsInRoot`)
 * de sus componentes para que el Spotlight del tutorial los recorte con precisión milimétrica.
 */
object TutorialSpotlightRegistry {
    val targets = mutableStateMapOf<SpotlightTargetId, Rect>()

    fun updateTarget(id: SpotlightTargetId, rect: Rect) {
        if (rect.width > 0f && rect.height > 0f) {
            targets[id] = rect
        }
    }
}

/**
 * Paso del recorrido visual (Fase 1: Coach Marks con Spotlight sobre la pantalla real).
 */
private data class SpotlightStep(
    val targetId: SpotlightTargetId,
    val badgeText: String,
    val title: String,
    val description: String,
    val tipText: String? = null,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Fases del tutorial interactivo inicial.
 */
private enum class TutorialPhase {
    WELCOME_INTRO,
    VISUAL_SPOTLIGHT_TOUR,
    GESTURE_TRAINING_LAB,
    COMPLETED_SUMMARY
}

/**
 * Dispara una vibración háptica suave de confirmación cuando el usuario realiza bien un gesto.
 */
private fun triggerSuccessVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 65, 50, 110), -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(140)
        }
    } catch (_: Exception) {
    }
}

/**
 * Overlay completo del Tutorial Inicial Interactivo de Punto de Información ETSIIT.
 *
 * Estructura en 2 bloques principales:
 * 1. **Recorrido Visual por la Interfaz (Spotlight / Coach Marks)**: ilumina cada zona clave de la
 *    pantalla real con un recorte brillante animado y explica para qué sirve paso a paso.
 * 2. **Laboratorio Interactivo de Gestos (Sensores Reales)**: guía al usuario gesto a gesto
 *    escuchando el sensor de proximidad, acelerómetro y pantalla multitáctil en tiempo real,
 *    mostrando "¡Lo has hecho bien! ✓" cuando el usuario ejecuta correctamente cada gesto.
 */
@Composable
fun OnboardingTutorialOverlay(
    onFinishTutorial: () -> Unit
) {
    val context = LocalContext.current
    var currentPhase by remember { mutableStateOf(TutorialPhase.WELCOME_INTRO) }
    var spotlightStepIndex by remember { mutableIntStateOf(0) }
    var gestureStepIndex by remember { mutableIntStateOf(0) }

    val spotlightSteps = remember {
        listOf(
            SpotlightStep(
                targetId = SpotlightTargetId.PROFILE_ICON,
                badgeText = "1 DE 7",
                title = "Identificación",
                description = "Pulsa este icono para identificarte escaneando el QR de tu TUI.\n\nAl identificarte se cargarán automáticamente tus asignaturas, grupo y horario personalizado.",
                tipText = null,
                icon = Icons.Default.Person,
                accentColor = UgrRed
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.GESTURE_BADGES,
                badgeText = "2 DE 7",
                title = "Gestos",
                description = "Aquí arriba verás siempre los iconos de los gestos físicos que puedes usar en cada pantalla y el botón «i» con la guía completa.",
                tipText = "Siempre que veas por la pantalla uno de estos logos, significa que ese gesto está activo y puedes usarlo.",
                icon = Icons.Default.Vibration,
                accentColor = EtsiitBlue
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.HOME_NEXT_CLASS,
                badgeText = "3 DE 7",
                title = "Próxima Clase",
                description = "Aquí verás la clase que tienes en curso o la siguiente del día y el aula donde se imparte. También puedes consultar tu clase agitando el móvil tres veces hacia ti.",
                tipText = null,
                icon = Icons.Default.School,
                accentColor = UgrRed
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.HOME_NEWS,
                badgeText = "4 DE 7",
                title = "Noticias",
                description = "Aquí aparecen las últimas noticias, avisos y eventos oficiales de la ETSIIT.",
                tipText = null,
                icon = Icons.Default.Public,
                accentColor = CampusGreen
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.HOME_SCHEDULE_BUTTON,
                badgeText = "5 DE 7",
                title = "Mi Horario",
                description = "Consulta tu cuadrante de horario semanal por asignaturas y colores.",
                tipText = null,
                icon = Icons.Default.DateRange,
                accentColor = Color(0xFF1D4ED8)
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.HOME_SERVICES_BUTTON,
                badgeText = "6 DE 7",
                title = "Servicios",
                description = "Accede a los trámites de Secretaría y al directorio de profesores con sus despachos y estado de tutorías en vivo.",
                tipText = null,
                icon = Icons.Default.Info,
                accentColor = Color(0xFF0F766E)
            ),
            SpotlightStep(
                targetId = SpotlightTargetId.HOME_DINING_BUTTON,
                badgeText = "7 DE 7",
                title = "Comedores",
                description = "Consulta el menú día a día de los Comedores UGR, revisa los alérgenos de cada plato y abona tu ticket de comedor directamente desde el móvil.",
                tipText = null,
                icon = Icons.Default.Restaurant,
                accentColor = Color(0xFFD97706)
            )
        )
    }

    val gestureSteps = remember {
        listOf(
            GestureType.PROXIMITY_VOICE,
            GestureType.TRIPLE_SHAKE,
            GestureType.TAKE_ME_THERE,
            GestureType.ACTION_TRIGGER,
            GestureType.LIFT_PHONE,
            GestureType.PINCH_ZOOM
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Bloquea toques accidentales al fondo durante el tutorial */ }
            )
    ) {
        AnimatedContent(
            targetState = currentPhase,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "TutorialPhaseTransition"
        ) { phase ->
            when (phase) {
                TutorialPhase.WELCOME_INTRO -> {
                    WelcomeTutorialModal(
                        onStartFullTour = {
                            spotlightStepIndex = 0
                            currentPhase = TutorialPhase.VISUAL_SPOTLIGHT_TOUR
                        },
                        onStartGesturesOnly = {
                            gestureStepIndex = 0
                            currentPhase = TutorialPhase.GESTURE_TRAINING_LAB
                        },
                        onSkipTutorial = onFinishTutorial
                    )
                }

                TutorialPhase.VISUAL_SPOTLIGHT_TOUR -> {
                    val step = spotlightSteps[spotlightStepIndex]
                    val targetRect = TutorialSpotlightRegistry.targets[step.targetId]
                    VisualSpotlightTourView(
                        step = step,
                        stepIndex = spotlightStepIndex,
                        totalSteps = spotlightSteps.size,
                        targetRect = targetRect,
                        onPrevious = {
                            if (spotlightStepIndex > 0) {
                                spotlightStepIndex--
                            } else {
                                currentPhase = TutorialPhase.WELCOME_INTRO
                            }
                        },
                        onNext = {
                            if (spotlightStepIndex < spotlightSteps.lastIndex) {
                                spotlightStepIndex++
                            } else {
                                gestureStepIndex = 0
                                currentPhase = TutorialPhase.GESTURE_TRAINING_LAB
                            }
                        },
                        onSkipToGestures = {
                            gestureStepIndex = 0
                            currentPhase = TutorialPhase.GESTURE_TRAINING_LAB
                        },
                        onCloseTutorial = onFinishTutorial
                    )
                }

                TutorialPhase.GESTURE_TRAINING_LAB -> {
                    val currentGesture = gestureSteps[gestureStepIndex]
                    GestureTrainingLabView(
                        context = context,
                        gesture = currentGesture,
                        stepIndex = gestureStepIndex,
                        totalSteps = gestureSteps.size,
                        onPrevious = {
                            if (gestureStepIndex > 0) {
                                gestureStepIndex--
                            } else {
                                spotlightStepIndex = spotlightSteps.lastIndex
                                currentPhase = TutorialPhase.VISUAL_SPOTLIGHT_TOUR
                            }
                        },
                        onNext = {
                            if (gestureStepIndex < gestureSteps.lastIndex) {
                                gestureStepIndex++
                            } else {
                                currentPhase = TutorialPhase.COMPLETED_SUMMARY
                            }
                        },
                        onCloseTutorial = onFinishTutorial
                    )
                }

                TutorialPhase.COMPLETED_SUMMARY -> {
                    TutorialCompletedModal(
                        onFinish = onFinishTutorial,
                        onRepeatGestures = {
                            gestureStepIndex = 0
                            currentPhase = TutorialPhase.GESTURE_TRAINING_LAB
                        }
                    )
                }
            }
        }
    }
}

/**
 * Pantalla 0: Bienvenida estética al Tutorial Inicial con opción de hacer el recorrido completo,
 * ir directo a los gestos o saltar.
 */
@Composable
private fun WelcomeTutorialModal(
    onStartFullTour: () -> Unit,
    onStartGesturesOnly: () -> Unit,
    onSkipTutorial: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90F172A))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera con insignia de primera vez
                Surface(
                    shape = RoundedCornerShape(50),
                    color = UgrRed.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = UgrRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "BIENVENIDO/A · GUÍA INICIAL ÚNICA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = UgrRed,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icono central con degradado
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(UgrRed, Color(0xFFB91C1C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Descubre tu Punto de Información ETSIIT",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Te enseñamos en 1 minuto cómo moverte por la aplicación y cómo controlar el móvil con gestos físicos y voz sin tocar la pantalla.",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Resumen de las 2 partes del tutorial
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EtsiitBlue.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EtsiitBlue,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = "Recorrido Visual por la Pantalla",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CampusGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "2",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CampusGreen,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = "Entrenamiento de Gestos en Vivo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botón principal: Empezar recorrido completo
                Button(
                    onClick = onStartFullTour,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Empezar Tutorial Paso a Paso",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botón secundario: Ir directo a practicar gestos
                OutlinedButton(
                    onClick = onStartGesturesOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, EtsiitBlue.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EtsiitBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ir directo a practicar los gestos",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(onClick = onSkipTutorial) {
                    Text(
                        text = "Saltar ahora (podrás abrirlo luego pulsando «i»)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Fase 1: Recorrido Visual con recorte Spotlight real (BlendMode.Clear) sobre los elementos de la UI
 * y tarjeta flotante inteligente que se coloca arriba o abajo para no tapar nunca el elemento iluminado.
 */
@Composable
private fun VisualSpotlightTourView(
    step: SpotlightStep,
    stepIndex: Int,
    totalSteps: Int,
    targetRect: Rect?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipToGestures: () -> Unit,
    onCloseTutorial: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Animación de pulso para el halo del Spotlight
    val infiniteTransition = rememberInfiniteTransition(label = "SpotlightPulse")
    val pulseRadiusPadding by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePadding"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Determinamos si el elemento iluminado está en la mitad superior o inferior de la pantalla
    // para colocar la tarjeta explicativa en el lado opuesto y que nunca lo tape.
    val placeCardAtBottom = remember(targetRect, screenHeightPx) {
        if (targetRect == null) true
        else targetRect.center.y < (screenHeightPx * 0.52f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Lienzo oscuro con agujero transparente (Spotlight) sobre el widget real
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            // Fondo oscuro translúcido
            drawRect(color = Color(0xD90F172A))

            if (targetRect != null) {
                val pad = 8.dp.toPx()
                val holeTopLeft = Offset(
                    x = (targetRect.left - pad).coerceAtLeast(4f),
                    y = (targetRect.top - pad).coerceAtLeast(4f)
                )
                val holeSize = Size(
                    width = (targetRect.width + pad * 2),
                    height = (targetRect.height + pad * 2)
                )
                val cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                // Recorte transparente que deja ver el elemento real de la interfaz debajo
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Clear
                )

                // Borde exterior pulsante alrededor del elemento destacado
                val pulsePad = pulseRadiusPadding.dp.toPx() * 0.55f
                drawRoundRect(
                    color = step.accentColor.copy(alpha = pulseAlpha * 0.45f),
                    topLeft = Offset(holeTopLeft.x - pulsePad, holeTopLeft.y - pulsePad),
                    size = Size(holeSize.width + pulsePad * 2, holeSize.height + pulsePad * 2),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )

                // Borde nítido principal
                drawRoundRect(
                    color = Color.White,
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Pequeña línea indicadora desde el Spotlight hacia la tarjeta
                val lineX = targetRect.center.x.coerceIn(48.dp.toPx(), size.width - 48.dp.toPx())
                if (placeCardAtBottom) {
                    val startY = holeTopLeft.y + holeSize.height + 6.dp.toPx()
                    val endY = (startY + 28.dp.toPx()).coerceAtMost(size.height * 0.55f)
                    if (endY > startY) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(lineX, startY),
                            end = Offset(lineX, endY),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(
                            color = step.accentColor,
                            radius = 5.dp.toPx(),
                            center = Offset(lineX, endY)
                        )
                    }
                } else {
                    val startY = holeTopLeft.y - 6.dp.toPx()
                    val endY = (startY - 28.dp.toPx()).coerceAtLeast(size.height * 0.40f)
                    if (startY > endY) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(lineX, startY),
                            end = Offset(lineX, endY),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(
                            color = step.accentColor,
                            radius = 5.dp.toPx(),
                            center = Offset(lineX, endY)
                        )
                    }
                }
            }
        }

        // Tarjeta flotante explicativa (Coach Mark Card)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 28.dp),
            contentAlignment = if (placeCardAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (!placeCardAtBottom) 44.dp else 0.dp,
                        bottom = if (placeCardAtBottom) 16.dp else 0.dp
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Fila superior: Badge de paso + Botón cerrar/saltar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = step.accentColor.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = step.accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = step.badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = step.accentColor
                                )
                            }
                        }

                        IconButton(
                            onClick = onCloseTutorial,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar tutorial",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = step.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = step.description,
                        fontSize = 13.5.sp,
                        color = TextPrimary.copy(alpha = 0.85f),
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Consejo extra en cajita suave (solo si tipText no es nulo)
                    if (!step.tipText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = step.accentColor.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "💡 ${step.tipText}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Indicador de puntos de progreso + Botones Anterior / Siguiente
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Puntos de progreso
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalSteps) { idx ->
                                val isSelected = idx == stepIndex
                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .width(if (isSelected) 22.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) step.accentColor
                                            else Color(0xFFCBD5E1)
                                        )
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onPrevious,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Anterior",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Button(
                                onClick = onNext,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = step.accentColor),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = if (stepIndex < totalSteps - 1) "Siguiente" else "¡Probar Gestos!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (stepIndex < totalSteps - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onSkipToGestures,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Saltar recorrido e ir a los gestos →",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fase 2: Laboratorio Interactivo de Entrenamiento de Gestos con Sensores Reales.
 * Escucha en vivo el sensor de proximidad, el acelerómetro o el gesto multitáctil para validar
 * cuándo el usuario ejecuta correctamente el gesto, vibrando y mostrando "¡Lo has hecho bien! ✓".
 */
@Composable
private fun GestureTrainingLabView(
    context: Context,
    gesture: GestureType,
    stepIndex: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCloseTutorial: () -> Unit
) {
    // Estados en vivo de este gesto concreto (se reinician al cambiar de gesto)
    var isGestureCompleted by remember(gesture) { mutableStateOf(false) }
    var feedbackSubtitle by remember(gesture) { mutableStateOf("Esperando a que realices el gesto...") }
    var shakeCount by remember(gesture) { mutableIntStateOf(0) }
    var shakeTotal by remember(gesture) { mutableIntStateOf(3) }
    var liveTiltX by remember(gesture) { mutableFloatStateOf(0f) }
    var liveTiltY by remember(gesture) { mutableFloatStateOf(0f) }
    var liveTiltZ by remember(gesture) { mutableFloatStateOf(0f) }
    var isProximityNear by remember(gesture) { mutableStateOf(false) }
    var pinchScale by remember(gesture) { mutableFloatStateOf(1f) }

    val markGestureSuccess = remember(gesture) {
        { message: String ->
            if (!isGestureCompleted) {
                isGestureCompleted = true
                feedbackSubtitle = message
                triggerSuccessVibration(context)
            }
        }
    }

    // Suscripción a los sensores reales del dispositivo según el gesto actual
    DisposableEffect(gesture) {
        var proximityManager: ProximityManager? = null
        var accelerometerManager: AccelerometerManager? = null

        when (gesture) {
            GestureType.PROXIMITY_VOICE -> {
                val pm = ProximityManager(context).apply {
                    onStateChanged = { isNear, distance ->
                        isProximityNear = isNear
                        if (isNear) {
                            markGestureSuccess("¡Mano detectada en el sensor superior (${distance.toInt()} cm)! Asistente de voz activado.")
                        }
                    }
                    onNear = {
                        isProximityNear = true
                        markGestureSuccess("¡Sensor de proximidad activado correctamente!")
                    }
                }
                if (pm.isAvailable) {
                    pm.startListening()
                    proximityManager = pm
                } else {
                    feedbackSubtitle = "Este dispositivo no tiene sensor de proximidad físico. Usa el botón de prueba."
                }
            }

            GestureType.TRIPLE_SHAKE,
            GestureType.TAKE_ME_THERE,
            GestureType.ACTION_TRIGGER,
            GestureType.LIFT_PHONE -> {
                val am = AccelerometerManager(context).apply {
                    onSensorValuesChanged = { x, y, z ->
                        liveTiltX = x
                        liveTiltY = y
                        liveTiltZ = z
                        // Para LIFT_PHONE también comprobamos si el usuario ya puso el móvil bien vertical (Y > 7.8)
                        if (gesture == GestureType.LIFT_PHONE && y > 7.8f && abs(z) < 5.5f) {
                            markGestureSuccess("¡Móvil colocado en posición vertical frente a ti! Modo Paseo 360° activado.")
                        }
                    }
                    onShakeProgress = { count, total ->
                        if (gesture == GestureType.TRIPLE_SHAKE) {
                            shakeCount = count
                            shakeTotal = total
                            if (count in 1 until total && !isGestureCompleted) {
                                feedbackSubtitle = "¡Muy bien! Llevas $count de $total inclinaciones..."
                            }
                        }
                    }
                    onShakeDetected = {
                        if (gesture == GestureType.TRIPLE_SHAKE) {
                            shakeCount = shakeTotal
                            markGestureSuccess("¡Triple inclinación completada (3/3)! Se abriría tu próxima clase.")
                        }
                    }
                    onTakeMeThereGesture = {
                        if (gesture == GestureType.TAKE_ME_THERE) {
                            markGestureSuccess("¡Giro a la izquierda detectado! Ruta rápida «¡Llévame!» iniciada.")
                        }
                    }
                    onForwardTiltGesture = {
                        if (gesture == GestureType.ACTION_TRIGGER) {
                            markGestureSuccess("¡Inclinación hacia ti detectada! Acción contextual confirmada.")
                        }
                    }
                    onDeviceLifted = {
                        if (gesture == GestureType.LIFT_PHONE) {
                            markGestureSuccess("¡Levantamiento del móvil detectado! Cámara / Vista 360° lista.")
                        }
                    }
                    onPostureModeChanged = { isVertical ->
                        if (gesture == GestureType.LIFT_PHONE && isVertical) {
                            markGestureSuccess("¡Postura vertical detectada! Modo Paseo 360° activado.")
                        }
                    }
                }
                if (am.isAvailable) {
                    am.startListening()
                    accelerometerManager = am
                } else {
                    feedbackSubtitle = "Este dispositivo no tiene acelerómetro físico. Usa el botón de prueba."
                }
            }

            GestureType.PINCH_ZOOM -> {
                feedbackSubtitle = "Coloca 2 dedos en el recuadro inferior y sepáralos o júntalos."
            }
        }

        onDispose {
            proximityManager?.stopListening()
            accelerometerManager?.stopListening()
        }
    }

    val cardBorderColor = if (isGestureCompleted) CampusGreen else gesture.badgeColor.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60F172A))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.5.dp, cardBorderColor, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra superior: Fase 2 + Paso X de 6 + Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = (if (isGestureCompleted) CampusGreen else gesture.badgeColor).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "ENTRENAMIENTO DE GESTOS · ${stepIndex + 1} DE $totalSteps",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isGestureCompleted) CampusGreen else gesture.badgeColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseTutorial,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar tutorial",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de progreso de los 6 gestos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(totalSteps) { idx ->
                        val color = when {
                            idx < stepIndex -> CampusGreen
                            idx == stepIndex && isGestureCompleted -> CampusGreen
                            idx == stepIndex -> gesture.badgeColor
                            else -> Color(0xFFE2E8F0)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icono circular grande del gesto (cambia a Check verde cuando lo hace bien)
                val iconScale by animateFloatAsState(
                    targetValue = if (isGestureCompleted) 1.12f else 1.0f,
                    animationSpec = tween(300),
                    label = "iconScale"
                )
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(
                            if (isGestureCompleted) CampusGreen.copy(alpha = 0.16f)
                            else gesture.badgeColor.copy(alpha = 0.14f)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isGestureCompleted) CampusGreen else gesture.badgeColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGestureCompleted) Icons.Default.Check else gesture.icon,
                        contentDescription = gesture.title,
                        tint = if (isGestureCompleted) CampusGreen else gesture.badgeColor,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chip con el nombre del movimiento físico
                Surface(
                    shape = RoundedCornerShape(50),
                    color = gesture.badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = gesture.gestureName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = gesture.badgeColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = gesture.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Frase única y directa que unifica cómo hacerlo y su función
                val unifiedInstruction = when (gesture) {
                    GestureType.PROXIMITY_VOICE -> "Pasa la mano a poca distancia por la parte superior del móvil para activar el asistente de voz."
                    GestureType.TRIPLE_SHAKE -> "Inclina el móvil adelante y atrás tres veces seguidas para consultar tu próxima clase."
                    GestureType.TAKE_ME_THERE -> "Inclina el móvil hacia la izquierda y vuelve a la posición inicial para iniciar la ruta."
                    GestureType.ACTION_TRIGGER -> "Inclina el móvil hacia ti con un cabeceo rápido y vuelve para confirmar la acción."
                    GestureType.LIFT_PHONE -> "Coloca el móvil en posición vertical frente a ti para activar la vista 360°."
                    GestureType.PINCH_ZOOM -> "Pellizca con dos dedos sobre la pantalla para ajustar el zoom en mapas y horarios."
                }

                Text(
                    text = unifiedInstruction,
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // RECUADRO ÚNICO INTERACTIVO: "¡Ahora pruébalo tú!" (unificado sin logos repetidos)
                GestureLiveTelemetryBox(
                    gesture = gesture,
                    isCompleted = isGestureCompleted,
                    feedbackSubtitle = feedbackSubtitle,
                    shakeCount = shakeCount,
                    shakeTotal = shakeTotal,
                    liveTiltX = liveTiltX,
                    liveTiltY = liveTiltY,
                    liveTiltZ = liveTiltZ,
                    isProximityNear = isProximityNear,
                    pinchScale = pinchScale,
                    onPinchScaleChange = { newScale ->
                        pinchScale = newScale
                        if (abs(newScale - 1f) >= 0.15f) {
                            markGestureSuccess("¡Zoom multitáctil detectado (${(newScale * 100).toInt()}%)!")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botones de navegación e intento simulado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Gesto anterior",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!isGestureCompleted) {
                        OutlinedButton(
                            onClick = {
                                markGestureSuccess("¡Gesto validado correctamente! Ya puedes avanzar.")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = "Simular / Saltar paso",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Button(
                            onClick = onNext,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CampusGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = if (stepIndex < totalSteps - 1) "Siguiente gesto" else "¡Finalizar Tutorial!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recuadro único interactivo de prueba para cada gesto físico.
 */
@Composable
private fun GestureLiveTelemetryBox(
    gesture: GestureType,
    isCompleted: Boolean,
    feedbackSubtitle: String,
    shakeCount: Int,
    shakeTotal: Int,
    liveTiltX: Float,
    liveTiltY: Float,
    liveTiltZ: Float,
    isProximityNear: Boolean,
    pinchScale: Float,
    onPinchScaleChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isCompleted) CampusGreen.copy(alpha = 0.12f) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (isCompleted) CampusGreen else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isCompleted) "¡Lo has hecho bien! ✓" else "¡Ahora pruébalo tú!",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = if (isCompleted) CampusGreen else TextPrimary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (isCompleted) feedbackSubtitle else "Esperando a que realices el movimiento...",
                fontSize = 12.sp,
                color = if (isCompleted) Color(0xFF065F46) else TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            when (gesture) {
                GestureType.PROXIMITY_VOICE -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isProximityNear || isCompleted) CampusGreen.copy(alpha = 0.18f) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = if (isProximityNear || isCompleted) "✓ Mano detectada en el sensor" else "✋ Esperando tu mano en el sensor...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProximityNear || isCompleted) CampusGreen else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                GestureType.TRIPLE_SHAKE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..shakeTotal) {
                            val reached = isCompleted || shakeCount >= i
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (reached) CampusGreen else Color(0xFFE2E8F0))
                                    .border(
                                        width = 2.dp,
                                        color = if (reached) CampusGreen else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (reached) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = "$i",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                GestureType.TAKE_ME_THERE -> {
                    val normalizedOffset = (-liveTiltX / 6.5f).coerceIn(-1f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(72.dp)
                                .background(CampusGreen.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⬅ META",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CampusGreen
                            )
                        }
                        Box(
                            modifier = Modifier
                                .offset(x = (normalizedOffset * 90).dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) CampusGreen else gesture.badgeColor)
                        )
                    }
                }

                GestureType.ACTION_TRIGGER -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isCompleted) CampusGreen.copy(alpha = 0.18f) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = if (isCompleted) "✓ Inclinación hacia ti detectada" else "📲 Inclina la parte superior hacia ti y vuelve",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) CampusGreen else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                GestureType.LIFT_PHONE -> {
                    val verticality = (liveTiltY / 9.0f).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nivel de verticalidad: ${(if (isCompleted) 100 else (verticality * 100).toInt())}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) CampusGreen else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (isCompleted) 1f else verticality },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (isCompleted) CampusGreen else gesture.badgeColor,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }

                GestureType.PINCH_ZOOM -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCompleted) CampusGreen.copy(alpha = 0.12f)
                                else gesture.badgeColor.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isCompleted) CampusGreen else gesture.badgeColor.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    val updated = (pinchScale * zoom).coerceIn(0.55f, 2.2f)
                                    onPinchScaleChange(updated)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pellizca aquí con 2 dedos · Zoom: ${(pinchScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) CampusGreen else gesture.badgeColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pantalla final de celebración cuando el usuario completa el tutorial.
 * Recuerda que no volverá a mostrarse automáticamente y dónde puede repetirse.
 */
@Composable
private fun TutorialCompletedModal(
    onFinish: () -> Unit,
    onRepeatGestures: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90F172A))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CampusGreen.copy(alpha = 0.16f))
                        .border(2.dp, CampusGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = CampusGreen,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¡Tutorial Completado!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Este tutorial no volverá a mostrarse automáticamente.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Si se quiere repetir más adelante, pulsa el botón «i» de la barra superior o accede desde Mi Perfil.",
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CampusGreen)
                ) {
                    Text(
                        text = "¡Empezar a usar la aplicación!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onRepeatGestures) {
                    Text(
                        text = "Volver a practicar los gestos",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EtsiitBlue
                    )
                }
            }
        }
    }
}
