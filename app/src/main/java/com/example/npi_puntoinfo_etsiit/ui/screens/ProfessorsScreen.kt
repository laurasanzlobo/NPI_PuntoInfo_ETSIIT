package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorNavigationRoute
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import org.json.JSONObject

data class Professor(
    val name: String,
    val subject: String,
    val office: String,
    val officeHours: String,
    val email: String,
    val isMyTeacher: Boolean = false
)

@Composable
fun ProfessorsScreen(
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedProfessor by remember { mutableStateOf<Professor?>(null) }

    val professorsList = remember { loadProfessors(context) }

    // El acelerómetro con el gesto «¡Llévame!» SOLO está activo si el usuario está dentro del detalle de un profesor
    DisposableEffect(selectedProfessor) {
        val manager = AccelerometerManager(context)

        if (selectedProfessor != null) {
            manager.onTakeMeThereGesture = {
                triggerVibration(context, durationMs = 150)
                val prof = selectedProfessor
                if (prof != null) {
                    val roomName = prof.office.split("·")[0].trim()
                    val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                    selectedProfessor = null
                    val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                    onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                }
            }

            manager.onForwardTiltGesture = {
                triggerVibration(context, durationMs = 150)
                val prof = selectedProfessor
                if (prof != null) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:${prof.email}")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
                    }
                    context.startActivity(intent)
                }
            }

            manager.startListening()
        }

        onDispose {
            manager.stopListening()
        }
    }

    // Diálogo con la información detallada al pulsar un profesor
    if (selectedProfessor != null) {
        val prof = selectedProfessor!!
        AlertDialog(
            onDismissRequest = { selectedProfessor = null },
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            iconContentColor = CampusTeal,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CampusTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = CampusTeal,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = prof.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = prof.subject,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (prof.isMyTeacher) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = UgrRed.copy(alpha = 0.12f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = UgrRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TU PROFESOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UgrRed
                                )
                            }
                        }
                    }

                    // Datos de despacho, tutorías y correo
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = prof.office,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                }
                                // Loguito arriba a la derecha del recuadro para el gesto «¡Llévame!»
                                GestureBadge(
                                    type = GestureType.TAKE_ME_THERE,
                                    size = 24.dp,
                                    iconSize = 14.dp,
                                    onClick = {
                                        val roomName = prof.office.split("·")[0].trim()
                                        val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                        selectedProfessor = null
                                        val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                                        onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tutorías: ${prof.officeHours}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = prof.email,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }
                                // Loguito al lado del correo para el gesto de enviar correo
                                GestureBadge(
                                    type = GestureType.ACTION_TRIGGER,
                                    size = 24.dp,
                                    iconSize = 14.dp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProfessor = null }) {
                    Text("Cerrar", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp)
    ) {
        Text(
            text = "Consulta despachos, tutorías y envía correos",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lista simplificada de profesores
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(professorsList) { prof ->
                ProfessorListItem(
                    prof = prof,
                    onClick = { selectedProfessor = prof }
                )
            }
        }
    }
}

/**
 * Fila simplificada en la lista principal de profesores
 */
@Composable
private fun ProfessorListItem(
    prof: Professor,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CampusTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = CampusTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = prof.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        if (prof.isMyTeacher) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Tu profesor",
                                tint = UgrRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = prof.subject,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Ver detalles",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
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
    }
}

fun loadProfessors(context: Context): List<Professor> {
    val jsonString = context.assets.open("docentes.json").bufferedReader().use { it.readText() }
    val jsonArray = JSONObject(jsonString).getJSONArray("profesores")
    val list = mutableListOf<Professor>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val clasesArray = obj.getJSONArray("clases")
        val subjectText = if (clasesArray.length() > 0) clasesArray.getJSONObject(0).getString("asignatura") else obj.getString("departamento")

        list.add(
            Professor(
                name = obj.getString("nombre"),
                subject = subjectText,
                office = obj.getString("despacho"),
                officeHours = obj.getString("tutorias"),
                email = obj.getString("email"),
                isMyTeacher = obj.getBoolean("es_profesor_actual")
            )
        )
    }
    return list.sortedWith(compareByDescending<Professor> { it.isMyTeacher }.thenBy { it.name })
}
