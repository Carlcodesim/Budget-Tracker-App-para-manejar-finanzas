package com.example.presentation.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.domain.model.SavingsGoal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor oficial de integración con Google Wallet y generación de fichas digitales.
 * Explica y gestiona los requerimientos de Google Wallet (Issuer ID de Google Pay Console)
 * y proporciona utilidades reales de exportación, visualización y enlace con la app Wallet.
 */
object GoogleWalletManager {

    private const val WALLET_PACKAGE_NAME = "com.google.android.apps.walletnfcrel"
    private const val WALLET_PAY_PACKAGE_NAME = "com.google.android.apps.nfc.plugin"
    private const val WALLET_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.google.android.apps.walletnfcrel"
    private const val WALLET_WEB_URL = "https://wallet.google.com"
    private const val PREFS_NAME = "google_wallet_prefs"
    private const val KEY_ISSUER_ID = "google_wallet_issuer_id"

    /**
     * Obtiene el Issuer ID configurado por el usuario en preferencias locales.
     */
    fun getIssuerId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ISSUER_ID, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Guarda el Issuer ID de Google Pay / Wallet Console.
     */
    fun setIssuerId(context: Context, issuerId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ISSUER_ID, issuerId.trim()).apply()
    }

    /**
     * Comprueba si la aplicación Google Wallet o Google Pay está instalada en el dispositivo.
     */
    fun isWalletInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(WALLET_PACKAGE_NAME, 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo(WALLET_PAY_PACKAGE_NAME, 0)
                true
            } catch (e2: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("googlewallet://"))
                    intent.resolveActivity(pm) != null
                } catch (e3: Exception) {
                    false
                }
            }
        }
    }

    /**
     * Abre directamente la aplicación de Google Wallet o redirige a Google Play / Web.
     */
    fun openGoogleWallet(context: Context) {
        val pm = context.packageManager
        
        // 1. Intentar lanzar Google Wallet app
        val launchIntent = pm.getLaunchIntentForPackage(WALLET_PACKAGE_NAME)
            ?: pm.getLaunchIntentForPackage(WALLET_PAY_PACKAGE_NAME)
            
        if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "Abriendo Google Wallet...", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                // Continuar a fallback
            }
        }

        // 2. Intentar esquema URI googlewallet://
        try {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("googlewallet://")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (deepLinkIntent.resolveActivity(pm) != null) {
                context.startActivity(deepLinkIntent)
                Toast.makeText(context, "Abriendo Google Wallet...", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            // Continuar a web
        }

        // 3. Fallback a Google Wallet Web
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(WALLET_WEB_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            Toast.makeText(context, "Abriendo Google Wallet en el navegador...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(WALLET_PLAY_STORE_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "No se pudo abrir Google Wallet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Genera la estructura JSON oficial para un Generic Pass de Google Wallet (Google Wallet REST API).
     */
    fun generateGenericPassJson(issuerId: String, budgetLimit: Double, currentSpent: Double): String {
        val id = issuerId.ifBlank { "EMISOR_DEMO_3388" }
        val remaining = (budgetLimit - currentSpent).coerceAtLeast(0.0)
        val percent = if (budgetLimit > 0) ((currentSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0
        val monthYear = SimpleDateFormat("yyyyMM", Locale.US).format(Date())
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("es")).format(Date()).replaceFirstChar { it.uppercase() }

        return """
        {
          "iss": "$id",
          "aud": "google",
          "typ": "savetowallet",
          "payload": {
            "genericObjects": [
              {
                "id": "$id.BUDGET_$monthYear",
                "classId": "$id.BUDGET_CLASS",
                "genericType": "GENERIC_OTHER",
                "hexBackgroundColor": "#0A2540",
                "logo": {
                  "sourceUri": {
                    "uri": "https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/pass_google_logo.png"
                  },
                  "contentDescription": {
                    "defaultValue": {
                      "language": "es-ES",
                      "value": "Gestor de Presupuesto"
                    }
                  }
                },
                "cardTitle": {
                  "defaultValue": {
                    "language": "es-ES",
                    "value": "Control de Presupuesto Mensual"
                  }
                },
                "subheader": {
                  "defaultValue": {
                    "language": "es-ES",
                    "value": "$monthName"
                  }
                },
                "header": {
                  "defaultValue": {
                    "language": "es-ES",
                    "value": "Disponible: ${CurrencyFormatter.format(remaining)}"
                  }
                },
                "textModulesData": [
                  {
                    "id": "limit",
                    "header": "LÍMITE PRESUPUESTARIO",
                    "body": "${CurrencyFormatter.format(budgetLimit)}"
                  },
                  {
                    "id": "spent",
                    "header": "GASTADO ($percent%)",
                    "body": "${CurrencyFormatter.format(currentSpent)}"
                  }
                ],
                "barcode": {
                  "type": "QR_CODE",
                  "value": "BUDGET-$monthYear-$percent",
                  "alternateText": "BUDGET-$monthYear"
                }
              }
            ]
          }
        }
        """.trimIndent()
    }

    /**
     * Genera la ficha resumen del presupuesto mensual legible para compartir o copiar.
     */
    fun getBudgetPassSummary(budgetLimit: Double, currentSpent: Double): String {
        val remaining = (budgetLimit - currentSpent).coerceAtLeast(0.0)
        val percent = if (budgetLimit > 0) ((currentSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("es")).format(Date()).replaceFirstChar { it.uppercase() }

        return """
            ╔══════════════════════════════════════════╗
            ║    💳 FICHA FINANCIERA DIGITAL           ║
            ║    Control de Presupuesto Mensual        ║
            ╚══════════════════════════════════════════╝
            📅 Período: $monthName
            💰 Límite Presupuestado: ${CurrencyFormatter.format(budgetLimit)}
            💸 Total Gastado: ${CurrencyFormatter.format(currentSpent)} ($percent%)
            💵 Saldo Disponible: ${CurrencyFormatter.format(remaining)}
            📊 Estado: ${if (currentSpent > budgetLimit) "⚠️ LÍMITE SUPERADO" else "✅ DENTRO DEL PRESUPUESTO"}
            ════════════════════════════════════════════
            🔐 ID de Registro: PASS-BUDGET-${System.currentTimeMillis() % 1000000}
            Emitido por: Gestor de Presupuesto Personal
        """.trimIndent()
    }

    /**
     * Genera la ficha digital de una meta de ahorro.
     */
    fun getGoalPassSummary(goal: SavingsGoal): String {
        val percent = if (goal.targetAmount > 0) ((goal.currentAmount / goal.targetAmount) * 100).toInt().coerceIn(0, 100) else 0
        val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

        return """
            ╔══════════════════════════════════════════╗
            ║    🎯 FICHA DE META FINANCIERA           ║
            ║    Ahorro y Objetivos Personales         ║
            ╚══════════════════════════════════════════╝
            🎯 Meta: ${goal.name}
            💰 Objetivo: ${CurrencyFormatter.format(goal.targetAmount)}
            💵 Ahorrado Actual: ${CurrencyFormatter.format(goal.currentAmount)} ($percent%)
            ⏳ Restante: ${CurrencyFormatter.format(remaining)}
            📊 Estado: ${if (goal.currentAmount >= goal.targetAmount || percent >= 100) "🎉 ¡META COMPLETADA!" else "💪 En progreso ($percent%)"}
            ════════════════════════════════════════════
            🔐 ID de Registro: PASS-GOAL-${goal.id}-${System.currentTimeMillis() % 100000}
            Emitido por: Gestor de Presupuesto Personal
        """.trimIndent()
    }

    /**
     * Copia los detalles de la ficha de presupuesto al portapapeles.
     */
    fun copyBudgetPassToClipboard(context: Context, budgetLimit: Double, currentSpent: Double) {
        try {
            val passText = getBudgetPassSummary(budgetLimit, currentSpent)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Ficha Digital Presupuesto", passText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "✅ Ficha copiada al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo copiar la ficha", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copia los detalles de la meta al portapapeles.
     */
    fun copyGoalPassToClipboard(context: Context, goal: SavingsGoal) {
        try {
            val passText = getGoalPassSummary(goal)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Ficha Meta Financiera", passText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "✅ Ficha de meta copiada al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo copiar la ficha", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Comparte los detalles de la ficha digital usando el selector nativo de Android.
     */
    fun shareBudgetPass(context: Context, budgetLimit: Double, currentSpent: Double) {
        try {
            val passText = getBudgetPassSummary(budgetLimit, currentSpent)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "💳 Ficha Financiera - Presupuesto Mensual")
                putExtra(Intent.EXTRA_TEXT, passText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Ficha Digital").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir ficha digital", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Comparte los detalles de la meta de ahorro usando el selector nativo de Android.
     */
    fun shareGoalPass(context: Context, goal: SavingsGoal) {
        try {
            val passText = getGoalPassSummary(goal)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "🎯 Meta de Ahorro: ${goal.name}")
                putExtra(Intent.EXTRA_TEXT, passText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Meta de Ahorro").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir meta", Toast.LENGTH_SHORT).show()
        }
    }
}
