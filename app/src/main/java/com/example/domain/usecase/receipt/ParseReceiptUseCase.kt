package com.example.domain.usecase.receipt

import android.graphics.Bitmap
import com.example.domain.model.ParsedReceipt
import com.example.domain.repository.GeminiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseReceiptUseCase(
    private val geminiRepository: GeminiRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(bitmap: Bitmap, availableCategories: List<String>): ParsedReceipt = withContext(ioDispatcher) {
        geminiRepository.parseReceipt(bitmap, availableCategories)
    }
}
