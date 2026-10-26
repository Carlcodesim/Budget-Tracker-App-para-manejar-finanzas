package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de base de datos Room para las transacciones.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["accountId"]),
        Index(value = ["type", "date"]),
        Index(value = ["accountId", "date"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String, // "INCOME" o "EXPENSE"
    val categoryId: Long,
    val description: String?,
    val date: Long, // timestamp epoch millis
    val accountId: Long = 1L,
    val tags: String? = null
)

/**
 * Extensiones puras para evaluar el impacto contable en cuentas.
 */
fun TransactionEntity.isTransfer(): Boolean {
    return type == "TRANSFER" ||
        tags?.contains("TRANSFER", ignoreCase = true) == true ||
        description?.startsWith("Transferencia a", ignoreCase = true) == true ||
        description?.startsWith("Transferencia desde", ignoreCase = true) == true
}

fun TransactionEntity.isTransferIn(): Boolean {
    return isTransfer() && (
        tags?.contains("TRANSFER,IN", ignoreCase = true) == true ||
        tags?.contains("IN", ignoreCase = true) == true ||
        description?.contains("desde", ignoreCase = true) == true
    )
}

fun TransactionEntity.signedBalanceDelta(): Double {
    return when {
        isTransfer() -> if (isTransferIn()) amount else -amount
        type == "INCOME" -> amount
        else -> -amount
    }
}

