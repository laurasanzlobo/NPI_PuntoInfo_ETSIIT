package com.example.npi_puntoinfo_etsiit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.outdoor.Maneuver
import com.example.npi_puntoinfo_etsiit.outdoor.WalkingStep
import com.example.npi_puntoinfo_etsiit.ui.components.WalkingStepRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalkingStepPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersProviderDistanceAndInstructionWithoutRawHtmlOrInventedProgress() {
        compose.setContent {
            MaterialTheme {
                WalkingStepRow(WalkingStep("Gira a la <b>derecha</b> &amp; continúa", 25.0, Maneuver.RIGHT), 2)
            }
        }
        compose.onNodeWithText("Gira a la derecha & continúa").assertIsDisplayed()
        compose.onNodeWithText("2 · 25 m").assertIsDisplayed()
        compose.onNodeWithText("<b>", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Siguiente", substring = true).assertDoesNotExist()
    }
}
