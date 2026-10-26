package com.example.domain.usecase.receipt

import android.graphics.Bitmap
import com.example.domain.model.ParsedReceipt
import com.example.domain.repository.GeminiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ParseReceiptUseCaseDispatcherTest {

    private class FakeGeminiRepo : GeminiRepository {
        var callCount = 0
        override suspend fun generateResponse(systemPrompt: String, userPrompt: String): String = ""
        override suspend fun parseReceipt(
            imageBitmap: Bitmap,
            availableCategories: List<String>
        ): ParsedReceipt {
            callCount++
            return ParsedReceipt(
                amount = 45.0,
                merchant = "Supermercado",
                dateEpoch = 1700000000000L,
                suggestedCategory = "Comida",
                note = "Compra de prueba"
            )
        }
    }

    @Test
    fun `parseReceiptUseCase executes on injected dispatcher`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeGeminiRepo()
        val useCase = ParseReceiptUseCase(fakeRepo, ioDispatcher = testDispatcher)

        val dummyBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val result = useCase(dummyBitmap, listOf("Comida"))

        assertEquals(1, fakeRepo.callCount)
        assertEquals(45.0, result.amount)
        assertEquals("Supermercado", result.merchant)
    }
}
