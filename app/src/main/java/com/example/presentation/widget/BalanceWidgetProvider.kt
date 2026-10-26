package com.example.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.presentation.common.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Widget para mostrar el balance total financiero en la pantalla de inicio.
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Ejecutar cálculo en un hilo de fondo (Room no permite lecturas en el hilo principal)
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            
            // 1. Obtener balance de cuentas
            val accounts = try {
                db.accountDao().getAllAccounts().first()
            } catch (e: Exception) {
                emptyList()
            }
            val initialBalanceSum = accounts.sumOf { it.initialBalance }

            // 2. Obtener transacciones para sumar ingresos y restar gastos
            val transactions = try {
                db.transactionDao().getAllTransactions().first()
            } catch (e: Exception) {
                emptyList()
            }
            
            var transactionBalanceOffset = 0.0
            transactions.forEach { trans ->
                if (trans.type == "INCOME") {
                    transactionBalanceOffset += trans.amount
                } else if (trans.type == "EXPENSE") {
                    transactionBalanceOffset -= trans.amount
                }
            }

            val totalBalance = initialBalanceSum + transactionBalanceOffset

            // Formatear el balance con la moneda seleccionada
            withContext(Dispatchers.Main) {
                try {
                    CurrencyFormatter.init(context)
                } catch (e: Exception) {
                    // Ya inicializado o ignorar
                }
                val formattedBalance = CurrencyFormatter.format(totalBalance)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.balance_widget_layout)
                    views.setTextViewText(R.id.widget_balance, formattedBalance)

                    // Intent para abrir la aplicación al hacer clic en el widget
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_balance, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_subtitle, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Forzar actualización si cambia una transacción
        if (intent.action == ACTION_UPDATE_BALANCE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, BalanceWidgetProvider::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_UPDATE_BALANCE_WIDGET = "com.example.UPDATE_BALANCE_WIDGET"

        fun updateWidget(context: Context) {
            val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_BALANCE_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
}
