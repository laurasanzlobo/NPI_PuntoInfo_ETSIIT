package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    // Diálogos y avisos de gestos físicos
    var gestureNotice by remember { mutableStateOf<String?>(null) }
    var showShakeDialog by remember { mutableStateOf(false) }
    var showTakeMeThereDialog by remember { mutableStateOf(false) }
    var showLiftDialog by remember { mutableStateOf(false) }

    // Conexión del sensor en segundo plano con el ciclo de vida de Compose
    DisposableEffect(context) {
        val accelerometerManager = AccelerometerManager(context)

        // 1. Gesto de 3 sacudidas completado (Consulta de horario)
        accelerometerManager.onShakeDetected = {
            triggerVibration(context, durationMs = 150)
            showShakeDialog = true
        }

        // 2. Gesto de Giro de muñeca a la izquierda («¡Llévame!»)
        accelerometerManager.onTakeMeThereGesture = {
            triggerVibration(context, durationMs = 180)
            showTakeMeThereDialog = true
        }

        // 3. Gesto de levantar el dispositivo (Lift-to-Orient)
        accelerometerManager.onDeviceLifted = {
            triggerVibration(context, durationMs = 90)
            gestureNotice = "📱 ¡Móvil levantado! Modo de guiado vertical activado."
            showLiftDialog = true
        }

        accelerometerManager.startListening()

        onDispose {
            accelerometerManager.stopListening()
        }
    }

    // Diálogo 1: Gesto de 3 sacudidas (Horario actual)
    if (showShakeDialog) {
        AlertDialog(
            onDismissRequest = { showShakeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = null,
                    tint = UgrRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "¡3 Sacudidas detectadas!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Has completado el gesto de agitar. Consultando tu horario actual:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Nuevos Paradigmas de Interacción",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "12:00 - 14:00 · Aula 1.2 (Módulo Informática)",
                                fontSize = 13.sp,
                                color = UgrRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShakeDialog = false
                        onNavigate(Screen.Navigation.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("¡Llévame al aula!")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShakeDialog = false }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo 2: Gesto de giro de muñeca a la izquierda («¡Llévame!»)
    if (showTakeMeThereDialog) {
        AlertDialog(
            onDismissRequest = { showTakeMeThereDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = CampusGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "¡Gesto «¡Llévame!» detectado!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Has girado la muñeca hacia la izquierda. Activando guiado directo a tu próximo destino:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Destino: Aula 1.2 (Planta 1)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Distancia: ~35 metros · Tiempo estimado: 1 min",
                                fontSize = 12.sp,
                                color = CampusTeal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTakeMeThereDialog = false
                        onNavigate(Screen.Navigation.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CampusGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ver ruta en el mapa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTakeMeThereDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo 3: Gesto de levantar el móvil (Lift-to-Orient)
    if (showLiftDialog) {
        AlertDialog(
            onDismissRequest = { showLiftDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = EtsiitBlue,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "📱 ¡Móvil levantado!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Has levantado el móvil en posición vertical de marcha. ¿Quieres entrar directamente al guiado paso a paso en la ETSIIT?",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiftDialog = false
                        onNavigate(Screen.Navigation.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EtsiitBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Abrir guiado paso a paso")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiftDialog = false }) {
                    Text("Ahora no", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cabecera de bienvenida con degradado elegante
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(UgrRed, UgrRedDark)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Punto de Información",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "ETSIIT · Granada",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { onNavigate(Screen.Profile.route) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Perfil",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bienvenida, Laura. Tu campus inteligente al alcance de tu mano.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Banner reactivo de aviso de gestos (Móvil levantado)
        AnimatedVisibility(
            visible = gestureNotice != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            gestureNotice?.let { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CampusTeal.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CampusTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = notice,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = CampusTeal,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { gestureNotice = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar aviso",
                                tint = CampusTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Tarjeta destacada: Próxima clase (Vinculada al horario)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(CampusGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRÓXIMA CLASE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CampusGreen
                        )
                    }
                    Text(
                        text = "12:00 - 14:00",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Nuevos Paradigmas de Interacción",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Aula 1.2 · Planta 1 (Módulo Informática)",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onNavigate(Screen.Navigation.route) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "¡Llévame al aula!")
                }
            }
        }

        // Tarjeta informativa elegante de gestos físicos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UgrRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = UgrRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Gestos inteligentes (Acelerómetro)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Agita 3 veces para consultar tu horario o gira la muñeca a la izquierda para activar «¡Llévame!».",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Título de accesos rápidos
        Text(
            text = "Servicios y campus",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Cuadrícula de accesos directos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShortcutCard(
                title = "Horario",
                subtitle = "Ver semana",
                icon = Icons.Default.DateRange,
                color = EtsiitBlue,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Schedule.route) }
            )
            ShortcutCard(
                title = "Comedores",
                subtitle = "Menú de hoy",
                icon = Icons.Default.Restaurant,
                color = WarningAmber,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Dining.route) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShortcutCard(
                title = "Profesores",
                subtitle = "Tutorías y despacho",
                icon = Icons.Default.School,
                color = CampusTeal,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Professors.route) }
            )
            ShortcutCard(
                title = "Guiado ETSIIT",
                subtitle = "Plano interior",
                icon = Icons.Default.Navigation,
                color = UgrRed,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Navigation.route) }
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * Función auxiliar para emitir una vibración háptica al disparar un gesto
 */
private fun triggerVibration(context: Context, durationMs: Long = 120) {
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
        // En emuladores o dispositivos sin motor háptico se ignora silenciosamente
    }
}

