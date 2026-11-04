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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.recovery.DatabaseAccessGate
import com.example.data.recovery.DatabaseStartupState
import com.example.data.recovery.StrictRecoveryCoordinator
import com.example.presentation.common.components.PinLockScreen
import com.example.presentation.common.util.SecurityManager
import com.example.presentation.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La aplicación vuelve a su navegación normal solo tras una evaluación aislada
 * completa y una confirmación explícita de la persona usuaria.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* isGranted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BudgetTrackerApp
        val themeManager = app.appContainer.themeManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by themeManager.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val scope = rememberCoroutineScope()
            var isWorking by remember { mutableStateOf(false) }
            var isAuthorized by remember { mutableStateOf(DatabaseAccessGate.isNormalAccessAllowed()) }
            var statusMessage by remember {
                mutableStateOf(
                    DatabaseStartupState.current()?.message
                        ?: "La base de datos todavía no ha sido evaluada."
                )
            }
            var readyForAuthorization by remember { mutableStateOf(false) }
            var reviewFindings by remember { mutableStateOf(emptyList<String>()) }
            var correctPin by remember { mutableStateOf(SecurityManager.getPin(this) ?: "") }
            var isUnlocked by remember {
                mutableStateOf(!SecurityManager.isPinEnabled(this) || correctPin.isEmpty())
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                if (!isAuthorized || !DatabaseAccessGate.isNormalAccessAllowed()) {
                    StrictRecoveryProtectionScreen(
                        statusMessage = statusMessage,
                        isWorking = isWorking,
                        readyForAuthorization = readyForAuthorization,
                        reviewFindings = reviewFindings,
                        onPrepare = {
                            scope.launch {
                                isWorking = true
                                readyForAuthorization = false
                                reviewFindings = emptyList()
                                val result = withContext(Dispatchers.IO) {
                                    StrictRecoveryCoordinator.prepare(this@MainActivity)
                                }
                                when (result) {
                                    is StrictRecoveryCoordinator.PreparationResult.ReadyForAuthorization -> {
                                        readyForAuthorization = true
                                        statusMessage = "La copia aislada superó las validaciones. Se revisaron ${result.recordsExamined} registros. La base original permanece intacta."
                                    }
                                    is StrictRecoveryCoordinator.PreparationResult.ManualReviewRequired -> {
                                        statusMessage = "Se encontraron ${result.issueCount} observaciones. No se habilitará el acceso hasta que sean revisadas."
                                        reviewFindings = result.sampleFindings
                                    }
                                    is StrictRecoveryCoordinator.PreparationResult.Blocked -> {
                                        statusMessage = "${result.reasonCode}: ${result.message}"
                                    }
                                }
                                isWorking = false
                            }
                        },
                        onAuthorize = {
                            scope.launch {
                                isWorking = true
                                val result = withContext(Dispatchers.IO) {
                                    StrictRecoveryCoordinator.authorizeAfterExplicitUserConfirmation(this@MainActivity)
                                }
                                when (result) {
                                    is StrictRecoveryCoordinator.AuthorizationResult.Authorized -> {
                                        isAuthorized = DatabaseAccessGate.isNormalAccessAllowed()
                                        readyForAuthorization = false
                                        statusMessage = "La copia validada fue activada. La base original se conserva sin cambios."
                                    }
                                    is StrictRecoveryCoordinator.AuthorizationResult.Rejected -> {
                                        statusMessage = "No se autorizó el acceso: ${result.reasonCode}. La protección continúa activa."
                                        readyForAuthorization = false
                                    }
                                }
                                isWorking = false
                            }
                        }
                    )
                } else {
                    if (!isUnlocked && correctPin.isNotEmpty()) {
                        PinLockScreen(
                            correctPin = correctPin,
                            onUnlocked = { isUnlocked = true }
                        )
                    } else {
                        val navDestination = remember { intent?.getStringExtra("NAV_DESTINATION") }
                        val initialType = remember { intent?.getStringExtra("INITIAL_TYPE") }
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
}

@Composable
private fun StrictRecoveryProtectionScreen(
    statusMessage: String,
    isWorking: Boolean,
    readyForAuthorization: Boolean,
    reviewFindings: List<String>,
    onPrepare: () -> Unit,
    onAuthorize: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Protección de tus datos financieros",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "La aplicación no abrirá ni migrará la base original. Primero creará una copia privada, validará SQLite y Room y revisará la consistencia financiera en modo de solo lectura.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
            reviewFindings.forEach { finding ->
                Spacer(Modifier.height(6.dp))
                Text("• $finding", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            if (isWorking) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onPrepare,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !readyForAuthorization
                ) {
                    Text("Evaluar una copia aislada")
                }
                if (readyForAuthorization) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onAuthorize,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmo: activar la copia validada")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Las versiones antiguas o futuras, las bases dañadas y cualquier observación financiera permanecen en modo de protección. No se corrigen datos automáticamente.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
