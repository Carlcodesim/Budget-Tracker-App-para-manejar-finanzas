package com.example.domain.model

import java.util.Date

/**
 * Representa un movimiento de ingresos o gastos en la capa de dominio.
 */
data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val description: String?,
    val date: Date,
    val accountId: Long = 1L,
    val tags: String? = null
) {
    val isExpense: Boolean
        get() = type == TransactionType.EXPENSE

    val isIncome: Boolean
        get() = type == TransactionType.INCOME

    val isTransfer: Boolean
        get() = type == TransactionType.TRANSFER ||
            tags?.contains("TRANSFER", ignoreCase = true) == true ||
            description?.startsWith("Transferencia a", ignoreCase = true) == true ||
            description?.startsWith("Transferencia desde", ignoreCase = true) == true

    val isTransferIn: Boolean
        get() = isTransfer && (
            tags?.contains("TRANSFER,IN", ignoreCase = true) == true ||
            tags?.contains("IN", ignoreCase = true) == true ||
            description?.contains("desde", ignoreCase = true) == true
        )

    val isTransferOut: Boolean
        get() = isTransfer && (
            tags?.contains("TRANSFER,OUT", ignoreCase = true) == true ||
            tags?.contains("OUT", ignoreCase = true) == true ||
            description?.contains("Transferencia a", ignoreCase = true) == true
        )

    val transferCorrelationId: String?
        get() {
            if (!isTransfer || tags.isNullOrBlank()) return null
            val match = Regex("""(?:CORR|LINK):([a-zA-Z0-9_-]+)""").find(tags)
            return match?.groupValues?.get(1)
        }

    /**
     * Retorna el impacto firmado del movimiento sobre el balance de la cuenta.
     * Positivo para ingresos y transferencias recibidas; negativo para gastos y transferencias enviadas.
     */
    val signedBalanceDelta: Double
        get() = when {
            isTransfer -> if (isTransferIn) amount else -amount
            isIncome -> amount
            else -> -amount
        }
}

