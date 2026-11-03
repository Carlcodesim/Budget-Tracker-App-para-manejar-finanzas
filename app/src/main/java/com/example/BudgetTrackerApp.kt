package com.example

import android.app.Application
import com.example.di.AppContainer

/**
 * Clase Application principal que inicializa el contenedor de dependencias global de la aplicación
 * y configura los observadores reactivos para el widget de inicio.
 */
class BudgetTrackerApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        com.example.presentation.common.util.CurrencyFormatter.init(this)
        com.example.util.BudgetCycleManager.init(this)

        // Strict quarantine: do not start Room observers, schedulers, workers or alarms.
        // Existing background entry points are independently guarded before database access.

    }
}
