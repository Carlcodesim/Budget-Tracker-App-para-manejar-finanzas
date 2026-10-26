package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para la persistencia y consulta del inventario y datos comerciales de la tienda.
 */
interface StoreRepository {
    fun getInventoryValue(): Flow<Double>
    suspend fun saveInventoryValue(value: Double)
}
