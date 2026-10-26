package com.example.domain.repository

import com.example.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para operaciones con categorías.
 */
interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    fun getCategoriesByType(type: String): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun deleteCategoryById(id: Long)
    fun getTransactionCountForCategory(categoryId: Long): Flow<Int>
}
