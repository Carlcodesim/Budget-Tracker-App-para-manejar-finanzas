package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BusinessEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para las operaciones de base de datos de negocios.
 */
@Dao
interface BusinessDao {

    @Query("SELECT * FROM businesses ORDER BY createdAt ASC")
    fun getAllBusinesses(): Flow<List<BusinessEntity>>

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun getBusinessById(id: Long): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessEntity): Long

    @Update
    suspend fun updateBusiness(business: BusinessEntity)

    @Delete
    suspend fun deleteBusiness(business: BusinessEntity)

    @Query("UPDATE businesses SET inventoryValue = :value WHERE id = :id")
    suspend fun updateInventoryValue(id: Long, value: Double)

    @Query("SELECT COUNT(*) FROM businesses")
    suspend fun getBusinessCount(): Int
}
