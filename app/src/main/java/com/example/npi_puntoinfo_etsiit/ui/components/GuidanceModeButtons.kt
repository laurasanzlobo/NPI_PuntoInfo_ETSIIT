package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Selector permanente; elegir el modo actual no reinicia ni modifica el recorrido. */
@Composable
fun GuidanceModeButtons(outdoor: Boolean, onIndoor: () -> Unit, onOutdoor: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onIndoor,
            modifier = Modifier.weight(1f).testTag("open-indoor-guidance").semantics { selected = !outdoor },
            colors = ButtonDefaults.outlinedButtonColors(containerColor =
                if (!outdoor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
            Text("Guiado interior")
        }
        OutlinedButton(onClick = onOutdoor,
            modifier = Modifier.weight(1f).testTag("open-outdoor-guidance").semantics { selected = outdoor },
            colors = ButtonDefaults.outlinedButtonColors(containerColor =
                if (outdoor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
            Text("Guiado exterior")
        }
    }
}
