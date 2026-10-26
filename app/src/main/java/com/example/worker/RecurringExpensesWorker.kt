package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.di.AppContainer
import com.example.presentation.widget.BalanceWidgetProvider
import com.example.util.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker en segundo plano que procesa los gastos recurrentes mensuales pendientes
 * y los inserta automáticamente en la base de datos Room como transacciones oficiales.
 */
class RecurringExpensesWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val appContainer = AppContainer(context)
            val processResult = appContainer.processRecurringExpensesUseCase(
                targetYearMonth = null,
                forceAllDue = false
            )

            if (processResult.executedCount > 0) {
                // Notificar al usuario sobre los gastos recurrentes insertados en la base de datos
                NotificationScheduler.showRecurringExpensesProcessedNotification(
                    context = context,
                    count = processResult.executedCount,
                    totalAmount = processResult.totalAmount,
                    firstExpenseTitle = processResult.createdTransactions.firstOrNull()?.description
                )

                // Actualizar el widget de saldo en la pantalla de inicio
                BalanceWidgetProvider.updateWidget(context)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Si ocurre un error transitorio, permitir reintento según la política de WorkManager
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
