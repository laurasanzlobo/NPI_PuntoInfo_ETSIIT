package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun DiningScreen() {
    var orderPlaced by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Comedores Universitarios",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

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
                    Text(text = "Menú del Día · Hoy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text(text = "3.50 €", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = UgrRed)
                }
                Spacer(modifier = Modifier.height(12.dp))
                MenuItem(category = "1º Plato", dish = "Lentejas castellanas con verduras o Ensalada mixta")
                MenuItem(category = "2º Plato", dish = "Filete de merluza a la plancha o Pechuga de pollo")
                MenuItem(category = "Postre", dish = "Fruta de temporada o Yogur natural")
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Comedor Aynadamar · A 8 min a pie", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Encargo para llevar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (orderPlaced) Color(0xFFDCFCE7) else SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Fastfood, contentDescription = null, tint = if (orderPlaced) CampusGreen else WarningAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (orderPlaced) "¡Comida para llevar encargada!" else "Encargar comida para llevar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (orderPlaced) CampusGreen else TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (orderPlaced) "Recogida asignada a las 14:15 h en la ventanilla express." else "Puedes confirmar tu reserva mediante un gesto de interacción rápida.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { orderPlaced = !orderPlaced },
                    colors = ButtonDefaults.buttonColors(containerColor = if (orderPlaced) CampusGreen else WarningAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.TouchApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (orderPlaced) "Cancelar pedido" else "Confirmar encargo")
                }
            }
        }
    }
}

@Composable
private fun MenuItem(category: String, dish: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "$category: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
        Text(text = dish, fontSize = 13.sp, color = TextSecondary)
    }
}
