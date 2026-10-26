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
 * Receiver para recibir las alarmas de notificación de pago y mostrar
 * la notificación del sistema en el dispositivo de forma confiable.
 */
class ReminderNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationScheduler.ACTION_SHOW_REMINDER) {
            val pendingResult = goAsync()

            val reminderId = intent.getLongExtra(NotificationScheduler.EXTRA_REMINDER_ID, 0L)
            val title = intent.getStringExtra(NotificationScheduler.EXTRA_REMINDER_TITLE) ?: "Recordatorio de Pago"
            val type = intent.getStringExtra(NotificationScheduler.EXTRA_REMINDER_TYPE) ?: ""
            val amount = intent.getDoubleExtra(NotificationScheduler.EXTRA_REMINDER_AMOUNT, -1.0).takeIf { it > 0 }
            val notifyDaysBefore = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIFY_DAYS_BEFORE, 1)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Evitar spam o repeticiones múltiples en el mismo día
                    if (!ReminderNotificationManager.hasAlreadyNotifiedToday(context, reminderId)) {
                        val contentText = NotificationScheduler.buildReminderContentText(
                            type = type,
                            title = title,
                            amount = amount,
                            notifyDaysBefore = notifyDaysBefore
                        )

                        // 1. Mostrar la notificación del sistema en la barra de estado
                        NotificationScheduler.showNotification(
                            context = context,
                            reminderId = reminderId,
                            title = title,
                            contentText = contentText,
                            type = type,
                            amount = amount
                        )

                        // 2. Registrar en preferencias que se notificó hoy
                        ReminderNotificationManager.recordNotifiedToday(context, reminderId)
                    }

                    // 3. Reprogramar automáticamente para el siguiente ciclo futuro
                    val app = context.applicationContext as? com.example.BudgetTrackerApp
                    val appContainer = app?.appContainer ?: AppContainer(context)
                    val allReminders = appContainer.reminderRepository.getAllReminders().first()
                    val reminder = allReminders.find { it.id == reminderId }
                    if (reminder != null && reminder.isEnabled) {
                        NotificationScheduler.scheduleReminder(context, reminder)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

