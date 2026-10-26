package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de base de datos Room para las cuentas financieras.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "CASH", "BANK", "DEBIT", "CREDIT"
    val initialBalance: Double,
    val colorHex: String = "#4CAF50"
)
