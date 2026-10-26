package com.example.domain.model

/**
 * Representa una cuenta financiera en la capa de dominio.
 */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: String, // "CASH", "BANK", "DEBIT", "CREDIT"
    val initialBalance: Double,
    val colorHex: String = "#4CAF50",
    val currentBalance: Double = initialBalance
)
