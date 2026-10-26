package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver para reprogramar las notificaciones de pago cuando el dispositivo se reinicia.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()

            // Reprogramar worker y alarmas de gastos recurrentes y recordatorio diario
            RecurringExpensesScheduler.scheduleAll(context)
            NotificationScheduler.scheduleDailyExpenseReminder(context)
            com.example.worker.RemindersSyncWorker.enqueue(context)

            val app = context.applicationContext as? com.example.BudgetTrackerApp
            val appContainer = app?.appContainer ?: AppContainer(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val allReminders = appContainer.reminderRepository.getAllReminders().first()
                    NotificationScheduler.scheduleAllActiveReminders(context, allReminders)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
