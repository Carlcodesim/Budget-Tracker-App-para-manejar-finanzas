package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.data.recovery.DatabasePreflightInspector
import com.example.data.recovery.ActiveDatabaseCopyRegistry
import com.example.data.recovery.DatabaseCondition
import com.example.data.recovery.DatabasePreflightResult
import com.example.data.recovery.DatabaseStartupState

/**
 * Clase Application principal que inicializa el contenedor de dependencias global de la aplicación
 * y configura los observadores reactivos para el widget de inicio.
 */
class BudgetTrackerApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Read only the SQLite header and sidecar presence. This does not use Room,
        // open SQLite, or grant normal database access.
        val startupInspection = when (val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(this)) {
            is ActiveDatabaseCopyRegistry.Source.LegacyOriginal ->
                DatabasePreflightInspector.inspect(source.file)
            is ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy ->
                DatabasePreflightInspector.inspect(source.file)
            is ActiveDatabaseCopyRegistry.Source.Rejected ->
                DatabasePreflightResult(
                    condition = DatabaseCondition.IO_ERROR,
                    reasonCode = source.reasonCode,
                    message = "No se pudo identificar con seguridad la última base activa. El acceso permanece bloqueado."
                )
        }
        DatabaseStartupState.publish(startupInspection)
        appContainer = AppContainer(this)
        com.example.presentation.common.util.CurrencyFormatter.init(this)
        com.example.util.BudgetCycleManager.init(this)

        // Strict quarantine: do not start Room observers, schedulers, workers or alarms.
        // Existing background entry points are independently guarded before database access.

    }
}
