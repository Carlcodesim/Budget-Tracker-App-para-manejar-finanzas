package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * BroadcastReceiver encargado de evaluar si el usuario ha registrado transacciones
 * al final de la jornada (ej. 20:30 hrs).
 * Si no se ha registrado ningún movimiento hoy, dispara una notificación local interactiva.
 */
class DailyExpenseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContainer = AppContainer(context)
                
                // Calcular el inicio y fin del día actual en la zona horaria local
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfDay = calendar.timeInMillis

                // Consultar las transacciones registradas hoy de forma síncrona en Room
                val todayTransactions = appContainer.transactionRepository
                    .getAllTransactions()
                
                // Usamos el DAO directamente a través de getTransactionsByDateRangeSync
                val database = appContainer.database
                val transactionsToday = database.transactionDao().getTransactionsByDateRangeSync(startOfDay, endOfDay)

                // Si NO se registró ninguna transacción en el día y el recordatorio está activo
                if (transactionsToday.isEmpty() && DailyReminderManager.isDailyReminderEnabled(context)) {
                    NotificationScheduler.showDailyExpenseReminderNotification(context)
                }

                // Reprogramar automáticamente para la jornada del día siguiente si sigue activo
                if (DailyReminderManager.isDailyReminderEnabled(context)) {
                    NotificationScheduler.scheduleDailyExpenseReminder(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
