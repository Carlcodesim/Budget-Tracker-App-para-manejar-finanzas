package com.example.domain.repository

import android.graphics.Bitmap
import com.example.domain.model.ParsedReceipt

interface GeminiRepository {
    suspend fun generateResponse(systemPrompt: String, userPrompt: String): String
    suspend fun parseReceipt(imageBitmap: Bitmap, availableCategories: List<String>): ParsedReceipt
}
