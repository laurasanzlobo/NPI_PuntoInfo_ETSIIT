package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

data class ClassItem(
    val time: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val isCurrent: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigate: (String) -> Unit
) {
    val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    val sampleSchedule = listOf(
        ClassItem("08:30 - 10:30", "Sistemas Distribuidos", "Aula 2.1 · Planta 2", "Prof. Alberto R.", false),
        ClassItem("10:30 - 12:00", "Ingeniería del Software", "Aula 1.4 · Planta 1", "Prof. María G.", false),
        ClassItem("12:00 - 14:00", "Nuevos Paradigmas de Interacción", "Aula 1.2 · Planta 1", "Prof. Roberto P.", true),
        ClassItem("15:30 - 17:30", "Seguridad en Redes", "Aula 0.3 · Planta Baja", "Prof. Carmen S.", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp)
    ) {
        Text(
            text = "Mi Horario Semanal",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Consulta tus clases y cómo llegar a cada aula",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Selector de días horizontal
        ScrollableTabRow(
            selectedTabIndex = selectedDayIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            days.forEachIndexed { index, day ->
                val isSelected = selectedDayIndex == index
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) UgrRed else SurfaceWhite)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { selectedDayIndex = index },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = day,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de clases del día
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleSchedule) { item ->
                ClassCard(item = item, onGuideClick = { onNavigate(Screen.Navigation.route) })
            }
        }
    }
}

@Composable
private fun ClassCard(
    item: ClassItem,
    onGuideClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCurrent) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (item.isCurrent) CampusGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.time,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isCurrent) CampusGreen else TextSecondary
                    )
                }

                if (item.isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CampusGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTUAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CampusGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.subject,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${item.room} · ${item.teacher}",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onGuideClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Ruta guiada a ${item.room.split("·")[0].trim()}", fontSize = 13.sp)
            }
        }
    }
}
