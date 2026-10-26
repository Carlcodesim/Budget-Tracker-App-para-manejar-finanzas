package com.example

import android.app.Application
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        // Observar datos reactivamente para actualizar el Widget de Inicio automáticamente
        val container = appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.getTransactionsUseCase().collect {
                    com.example.presentation.widget.BalanceWidgetProvider.updateWidget(this@BudgetTrackerApp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Inicializar canales de notificaciones de alta prioridad y programar recordatorios activos
        com.example.util.NotificationScheduler.createNotificationChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderRepository.getAllReminders().collect { reminders ->
                    com.example.util.NotificationScheduler.scheduleAllActiveReminders(this@BudgetTrackerApp, reminders)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Programar worker en segundo plano y alarma para inserción automática de gastos recurrentes en Room
        com.example.util.RecurringExpensesScheduler.scheduleAll(this)

        // Programar comprobación y notificación de recordatorio diario de gastos al final de la jornada
        com.example.util.NotificationScheduler.scheduleDailyExpenseReminder(this)

        // Programar sincronización periódica en segundo plano de recordatorios de pago con WorkManager
        com.example.worker.RemindersSyncWorker.enqueue(this)
    }
}
