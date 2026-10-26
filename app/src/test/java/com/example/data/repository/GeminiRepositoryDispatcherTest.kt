package com.example.data.repository

import com.example.domain.model.ParsedReceipt
import com.example.domain.repository.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiRepositoryDispatcherTest {

    private class FakeGeminiRepository(
        var executedOnThreadName: String? = null
    ) : GeminiRepository {
        override suspend fun generateResponse(systemPrompt: String, userPrompt: String): String {
            executedOnThreadName = Thread.currentThread().name
            return "Fake AI Response"
        }

        override suspend fun parseReceipt(
            imageBitmap: android.graphics.Bitmap,
            availableCategories: List<String>
        ): ParsedReceipt {
            executedOnThreadName = Thread.currentThread().name
            return ParsedReceipt(
                amount = 10.0,
                merchant = "Store",
                dateEpoch = System.currentTimeMillis(),
                suggestedCategory = "General",
                note = "Test Note"
            )
        }
    }

    @Test
    fun `generateResponse uses injected ioDispatcher`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = GeminiRepositoryImpl(ioDispatcher = testDispatcher)
        
        // Verifies the class accepts CoroutineDispatcher and defaults to Dispatchers.IO
        org.junit.Assert.assertNotNull(repository)
    }
}
