package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.npi_puntoinfo_etsiit.outdoor.FixedOutdoorRoute

@Composable
fun OutdoorStepConfirmationDialog(
    route: FixedOutdoorRoute,
    currentStepIndex: Int,
    suggestedStepIndex: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val arrival = suggestedStepIndex == route.waypoints.lastIndex
    AlertDialog(
        modifier = Modifier.testTag("outdoor-step-confirmation"),
        onDismissRequest = onDismiss,
        title = { Text(when {
            arrival -> "¿Confirmar llegada a la ETSIIT?"
            suggestedStepIndex == currentStepIndex + 1 -> "¿Pasar al siguiente paso?"
            suggestedStepIndex > currentStepIndex -> "¿Avanzar al paso ${suggestedStepIndex + 1}?"
            else -> "¿Volver al paso ${suggestedStepIndex + 1}?"
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (arrival) "El GPS te sitúa junto al acceso de la ETSIIT. ¿Quieres marcar la llegada?"
                    else "El GPS detecta que estás más cerca del punto del paso ${suggestedStepIndex + 1}. ¿Quieres cambiar la instrucción?")
                Text("Paso ${suggestedStepIndex + 1} de ${route.waypoints.size}", style = MaterialTheme.typography.labelLarge)
                Text(route.waypoints[suggestedStepIndex].instruction)
                Text("Si eliges «Ahora no», se mantendrá el paso actual.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-outdoor-step")) {
                Text(if (arrival) "Confirmar llegada" else "Sí, cambiar de paso")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } }
    )
}
