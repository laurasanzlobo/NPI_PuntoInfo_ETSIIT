package com.example.npi_puntoinfo_etsiit.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.ui.theme.TextSecondary
import com.example.npi_puntoinfo_etsiit.ui.theme.UgrRed

@Composable
fun WalkingStepRow(step: WalkingStep, number: Int) {
    val context = LocalContext.current
    var showPhotoDialog by remember { mutableStateOf(false) }

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

    if (showPhotoDialog && step.photo != null) {
        val bitmap = remember(step.photo) {
            loadSampledBitmapFromAssets(context, step.photo, 4)
        }

        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = step.nodeName ?: "Foto del paso",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = step.instruction.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPhotoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UgrRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "Cerrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "$number · ${formatOutdoorDistance(step.distanceMeters)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    HtmlCompat.fromHtml(step.instruction, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (step.photo != null) {
                IconButton(
                    onClick = { showPhotoDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Ver foto del entorno",
                        tint = UgrRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

internal fun loadSampledBitmapFromAssets(context: Context, path: String, sampleSize: Int = 4): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
