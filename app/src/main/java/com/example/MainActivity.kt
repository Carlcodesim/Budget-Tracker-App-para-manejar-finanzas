package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.presentation.common.components.PinLockScreen
import com.example.presentation.common.util.SecurityManager
import com.example.presentation.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode

/**
 * Actividad principal de la aplicación. Configura la visualización de borde a borde (Edge-to-Edge),
 * solicitud de permisos de notificaciones e inicia el grafo de navegación unificado bajo el tema estético global.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* isGranted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as BudgetTrackerApp).appContainer
        val themeManager = appContainer.themeManager

        // Solicitar permisos de notificación en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        enableEdgeToEdge()
        setContent {
            val themeMode by themeManager.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            // Comprobación de seguridad mediante PIN
            var isUnlocked by remember { 
                mutableStateOf(!SecurityManager.isPinEnabled(this) || SecurityManager.getPin(this) == null) 
            }
            val correctPin = remember { SecurityManager.getPin(this) ?: "" }

            val navDestination = remember { intent?.getStringExtra("NAV_DESTINATION") }
            val initialType = remember { intent?.getStringExtra("INITIAL_TYPE") }

            MyApplicationTheme(darkTheme = darkTheme) {
                if (!isUnlocked && correctPin.isNotEmpty()) {
                    PinLockScreen(
                        correctPin = correctPin,
                        onUnlocked = { isUnlocked = true }
                    )
                } else {
                    AppNavigation(
                        modifier = Modifier.fillMaxSize(),
                        initialDestination = navDestination,
                        initialType = initialType
                    )
                }
            }
        }
    }
}

