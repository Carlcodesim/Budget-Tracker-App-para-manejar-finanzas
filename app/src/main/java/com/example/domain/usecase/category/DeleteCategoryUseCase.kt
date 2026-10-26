package com.example.domain.usecase.category

import com.example.domain.model.Category
import com.example.domain.repository.CategoryRepository

/**
 * Caso de uso para eliminar una categoría.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(category: Category) {
        categoryRepository.deleteCategory(category)
    }

    suspend operator fun invoke(id: Long) {
        categoryRepository.deleteCategoryById(id)
    }
}
