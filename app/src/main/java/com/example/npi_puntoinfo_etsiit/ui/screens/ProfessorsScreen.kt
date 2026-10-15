package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.theme.*

data class Professor(
    val name: String,
    val subject: String,
    val office: String,
    val officeHours: String,
    val email: String,
    val isMyTeacher: Boolean = false
)

@Composable
fun ProfessorsScreen() {
    val professorsList = listOf(
        Professor("Roberto Pérez", "Nuevos Paradigmas de Interacción", "Despacho D-24 · Planta 2", "L y X de 10:00 a 12:00", "robertop@ugr.es", true),
        Professor("María Gómez", "Ingeniería del Software", "Despacho D-12 · Planta 1", "M y J de 11:30 a 13:30", "mariag@ugr.es", true),
        Professor("Alberto Ruiz", "Sistemas Distribuidos", "Despacho D-08 · Planta Baja", "V de 09:00 a 13:00", "albertor@ugr.es", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp)
    ) {
        Text(
            text = "Directorio de Profesores",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Consulta despachos, tutorías y envía correos",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(professorsList) { prof ->
                ProfessorCard(prof = prof)
            }
        }
    }
}

@Composable
private fun ProfessorCard(prof: Professor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CampusTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = CampusTeal)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = prof.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text(text = prof.subject, fontSize = 12.sp, color = TextSecondary)
                    }
                }

                if (prof.isMyTeacher) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(UgrRed.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = UgrRed, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "TU PROFESOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = UgrRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.MeetingRoom, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = prof.office, fontSize = 13.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Tutorías: ${prof.officeHours}", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Enviar correo a ${prof.email}", fontSize = 12.sp)
            }
        }
    }
}
