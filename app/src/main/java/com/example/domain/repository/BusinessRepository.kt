package com.example.domain.repository

import com.example.domain.model.Business
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para la gestión de negocios y sus inventarios.
 */
interface BusinessRepository {
    fun getAllBusinesses(): Flow<List<Business>>
    suspend fun getBusinessById(id: Long): Business?
    suspend fun insertBusiness(business: Business): Long
    suspend fun updateBusiness(business: Business)
    suspend fun deleteBusiness(business: Business)
    suspend fun updateInventoryValue(businessId: Long, value: Double)
}
