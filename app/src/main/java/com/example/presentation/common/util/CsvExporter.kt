package com.example.presentation.common.util

import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.util.Locale

/**
 * Utilidad para exportar transacciones a formato CSV de forma segura y consistente.
 */
object CsvExporter {
    fun generateTransactionsCsv(transactions: List<Transaction>): String {
        val builder = java.lang.StringBuilder()
        // Cabecera CSV
        builder.append("ID,Fecha,Tipo,Categoría,Monto,ID Cuenta,Descripción,Etiquetas\n")
        
        for (tx in transactions) {
            val id = tx.id.toString()
            val dateStr = DateFormatter.format(tx.date).escapeCsv()
            val typeStr = when (tx.type) {
                TransactionType.INCOME -> "Ingreso"
                TransactionType.EXPENSE -> "Gasto"
                TransactionType.TRANSFER -> "Transferencia"
            }
            val categoryStr = tx.category.name.escapeCsv()
            val amountStr = String.format(Locale.US, "%.2f", tx.amount)
            val accountStr = tx.accountId.toString()
            val descStr = (tx.description ?: "").escapeCsv()
            val tagsStr = (tx.tags ?: "").escapeCsv()
            
            builder.append("$id,$dateStr,$typeStr,$categoryStr,$amountStr,$accountStr,$descStr,$tagsStr\n")
        }
        return builder.toString()
    }

    private fun String.escapeCsv(): String {
        if (contains("\"") || contains(",") || contains("\n") || contains("\r")) {
            return "\"" + replace("\"", "\"\"") + "\""
        }
        return this
    }
}
