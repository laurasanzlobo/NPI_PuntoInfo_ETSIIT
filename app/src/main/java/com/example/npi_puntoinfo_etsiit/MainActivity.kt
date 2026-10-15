package com.example.npi_puntoinfo_etsiit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.npi_puntoinfo_etsiit.ui.navigation.AppNavigation
import com.example.npi_puntoinfo_etsiit.ui.theme.NPI_PuntoInfo_ETSIITTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NPI_PuntoInfo_ETSIITTheme {
                AppNavigation()
            }
        }
    }
}
