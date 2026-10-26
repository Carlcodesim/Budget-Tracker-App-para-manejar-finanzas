package com.example.data.mapper

import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.util.Date

/**
 * Funciones de extensión para mapear transacciones entre la capa de datos y el dominio.
 */
fun TransactionEntity.toDomain(category: Category): Transaction {
    val parsedType = try {
        TransactionType.valueOf(type)
    } catch (e: Exception) {
        if (type.contains("TRANSFER", ignoreCase = true) ||
            description?.startsWith("Transferencia a", ignoreCase = true) == true ||
            description?.startsWith("Transferencia desde", ignoreCase = true) == true
        ) {
            TransactionType.TRANSFER
        } else {
            TransactionType.EXPENSE
        }
    }

    val resolvedType = if (parsedType != TransactionType.TRANSFER &&
        (description?.startsWith("Transferencia a", ignoreCase = true) == true ||
         description?.startsWith("Transferencia desde", ignoreCase = true) == true ||
         tags?.contains("TRANSFER", ignoreCase = true) == true)
    ) {
        TransactionType.TRANSFER
    } else {
        parsedType
    }

    return Transaction(
        id = id,
        amount = amount,
        type = resolvedType,
        category = category,
        description = description,
        date = Date(date),
        accountId = accountId,
        tags = tags
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        amount = amount,
        type = type.name,
        categoryId = category.id,
        description = description,
        date = date.time,
        accountId = accountId,
        tags = tags
    )
}
