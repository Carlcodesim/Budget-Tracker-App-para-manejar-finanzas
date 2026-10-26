package com.example.data.repository

import com.example.data.local.dao.BusinessDao
import com.example.data.local.entity.toDomain
import com.example.data.local.entity.toEntity
import com.example.domain.model.Business
import com.example.domain.repository.BusinessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación de BusinessRepository utilizando Room Database.
 */
class BusinessRepositoryImpl(
    private val businessDao: BusinessDao
) : BusinessRepository {

    override fun getAllBusinesses(): Flow<List<Business>> {
        return businessDao.getAllBusinesses().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBusinessById(id: Long): Business? {
        return businessDao.getBusinessById(id)?.toDomain()
    }

    override suspend fun insertBusiness(business: Business): Long {
        return businessDao.insertBusiness(business.toEntity())
    }

    override suspend fun updateBusiness(business: Business) {
        businessDao.updateBusiness(business.toEntity())
    }

    override suspend fun deleteBusiness(business: Business) {
        businessDao.deleteBusiness(business.toEntity())
    }

    override suspend fun updateInventoryValue(businessId: Long, value: Double) {
        businessDao.updateInventoryValue(businessId, value)
    }
}
