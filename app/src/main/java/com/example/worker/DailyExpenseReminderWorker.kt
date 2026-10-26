package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BudgetTrackerApp
import com.example.util.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Worker en segundo plano ejecutado periódicamente por WorkManager para comprobar
 * si el usuario ha registrado transacciones durante el día.
 */
class DailyExpenseReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Solo disparar la comprobación si ya es el final de la jornada (a partir de las 20:00 hrs)
            if (currentHour >= 20) {
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

                val app = context.applicationContext as BudgetTrackerApp
                val database = app.appContainer.database
                val transactionsToday = database.transactionDao().getTransactionsByDateRangeSync(startOfDay, endOfDay)

                if (transactionsToday.isEmpty()) {
                    NotificationScheduler.showDailyExpenseReminderNotification(context)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
