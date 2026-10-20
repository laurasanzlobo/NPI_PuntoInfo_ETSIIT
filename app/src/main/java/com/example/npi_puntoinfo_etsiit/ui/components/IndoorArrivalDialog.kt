package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.npi_puntoinfo_etsiit.ui.theme.UgrRed

@Composable
fun IndoorArrivalDialog(manual: Boolean, onContinue: () -> Unit, onStayOutside: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("indoor-arrival-dialog"),
        onDismissRequest = onStayOutside,
        icon = { Icon(Icons.Default.School, null, Modifier.size(40.dp), tint = UgrRed) },
        title = {
            Text(if (manual) "Continuar por la entrada principal" else "Estás llegando a la ETSIIT",
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("¿Quieres continuar la navegación por el interior?", textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                Text("Confirma que estás en la entrada principal · Planta baja",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuar dentro") }
                OutlinedButton(onClick = onStayOutside, modifier = Modifier.fillMaxWidth()) { Text("Aún estoy fuera") }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun CompactIndoorEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.testTag("compact-indoor-entry"),
        colors = ButtonDefaults.buttonColors(containerColor = UgrRed)) {
        Text("Ya estoy dentro")
    }
}
