package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import com.example.presentation.widget.BalanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver para recibir las alarmas diarias de evaluación de gastos recurrentes.
 * Procesa los gastos del día actual y reprograma la alarma para el día siguiente.
 */
class RecurringExpenseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RecurringExpensesScheduler.ACTION_PROCESS_RECURRING_ALARM) {
            // 1. Reprogramar la alarma diaria para el siguiente día
            RecurringExpensesScheduler.scheduleDailyAlarm(context)

            // 2. Ejecutar procesamiento asíncrono e inserción en Room
            val appContainer = AppContainer(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = appContainer.processRecurringExpensesUseCase(
                        targetYearMonth = null,
                        forceAllDue = false
                    )

                    if (result.executedCount > 0) {
                        NotificationScheduler.showRecurringExpensesProcessedNotification(
                            context = context,
                            count = result.executedCount,
                            totalAmount = result.totalAmount,
                            firstExpenseTitle = result.createdTransactions.firstOrNull()?.description
                        )
                        BalanceWidgetProvider.updateWidget(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback a WorkManager
                    RecurringExpensesScheduler.runImmediateCheck(context)
                }
            }
        }
    }
}
