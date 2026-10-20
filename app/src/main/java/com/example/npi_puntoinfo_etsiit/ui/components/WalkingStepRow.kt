package com.example.npi_puntoinfo_etsiit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.example.npi_puntoinfo_etsiit.outdoor.*

@Composable
fun WalkingStepRow(step: WalkingStep, number: Int) {
    val icon = when (step.maneuver) {
        Maneuver.LEFT, Maneuver.KEEP_LEFT -> Icons.Default.TurnLeft
        Maneuver.RIGHT, Maneuver.KEEP_RIGHT -> Icons.Default.TurnRight
        Maneuver.SHARP_LEFT -> Icons.Default.TurnSharpLeft
        Maneuver.SHARP_RIGHT -> Icons.Default.TurnSharpRight
        Maneuver.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        Maneuver.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        Maneuver.STRAIGHT, Maneuver.DEPART -> Icons.Default.Straight
        Maneuver.ROUNDABOUT, Maneuver.EXIT_ROUNDABOUT -> Icons.Default.RoundaboutRight
        Maneuver.U_TURN -> Icons.Default.UTurnLeft
        Maneuver.ARRIVAL -> Icons.Default.Flag
        Maneuver.UNKNOWN -> Icons.AutoMirrored.Filled.AltRoute
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$number · ${formatOutdoorDistance(step.distanceMeters)}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(HtmlCompat.fromHtml(step.instruction, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim(),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
