package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.data.recovery.DatabasePreflightInspector
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
        DatabaseStartupState.publish(
            DatabasePreflightInspector.inspect(getDatabasePath("budget_tracker_database"))
        )
        appContainer = AppContainer(this)
        com.example.presentation.common.util.CurrencyFormatter.init(this)
        com.example.util.BudgetCycleManager.init(this)

        // Strict quarantine: do not start Room observers, schedulers, workers or alarms.
        // Existing background entry points are independently guarded before database access.

    }
}
