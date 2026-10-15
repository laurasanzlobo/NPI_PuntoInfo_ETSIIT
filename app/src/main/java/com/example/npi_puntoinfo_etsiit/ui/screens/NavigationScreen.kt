package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun NavigationScreen() {
    var selectedTab by remember { mutableIntStateOf(1) } // 0: Exterior, 1: Interior

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Navegación y Guiado",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Selector Exterior / Interior
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceWhite,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Exterior (GPS Campus)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Interior (ETSIIT)") }
            )
        }

        if (selectedTab == 0) {
            // Modo Exterior (GPS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = EtsiitBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ubicación exterior en tiempo real", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Calculando distancias a pie hacia Comedores Universitarios, Bibliotecas y paradas de metro.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EtsiitBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver ruta a Comedores (Fuentenueva)")
                    }
                }
            }
        } else {
            // Modo Interior (ETSIIT Grafo)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Ruta activa: Aula 1.2", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text(text = "35 m · ~45 pasos", fontSize = 12.sp, color = UgrRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Origen: Entrada Principal (Planta 0) → Destino: Aula 1.2 (Planta 1)", fontSize = 13.sp, color = TextSecondary)
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Indicación inmersiva
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.TurnRight, contentDescription = null, tint = UgrRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Gira a la derecha en el pasillo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Sube por las escaleras hacia la Planta 1", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ScreenRotation, contentDescription = null, tint = CampusTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Levanta el móvil en vertical para el modo inmersivo con brújula", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}
