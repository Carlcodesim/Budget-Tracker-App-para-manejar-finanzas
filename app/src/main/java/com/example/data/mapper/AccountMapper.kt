package com.example.data.mapper

import com.example.data.local.entity.AccountEntity
import com.example.domain.model.Account

/**
 * Funciones de extensión para mapear cuentas entre la capa de datos y el dominio.
 */
fun AccountEntity.toDomain(currentBalance: Double = initialBalance): Account {
    return Account(
        id = id,
        name = name,
        type = type,
        initialBalance = initialBalance,
        colorHex = colorHex,
        currentBalance = currentBalance
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        name = name,
        type = type,
        initialBalance = initialBalance,
        colorHex = colorHex
    )
}
