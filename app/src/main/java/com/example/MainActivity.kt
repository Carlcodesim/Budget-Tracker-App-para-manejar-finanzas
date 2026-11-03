package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.data.recovery.DatabaseAccessGate

/**
 * Actividad principal de la aplicación. Configura la visualización de borde a borde (Edge-to-Edge),
 * solicitud de permisos de notificaciones e inicia el grafo de navegación unificado bajo el tema estético global.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // This branch remains in a dedicated recovery-safe screen. It deliberately
        // does not create a ViewModel, navigate to financial screens, or obtain Room.
        val permit = DatabaseAccessGate.currentPermit()
        setContent {
            MyApplicationTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Modo de protección activo",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = "El acceso a la base de datos está bloqueado mientras terminamos la validación y recuperación segura."
                        )
                        Text(
                            modifier = Modifier.padding(top = 12.dp),
                            text = "No se abrirá, migrará ni reemplazará tu base de datos original."
                        )
                        Text(
                            modifier = Modifier.padding(top = 12.dp),
                            text = "Estado de acceso: $permit",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

