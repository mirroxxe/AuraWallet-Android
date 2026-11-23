package com.aurawallet.app

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.toAppError
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase
import com.aurawallet.app.fakes.InMemoryHistoryStore
import com.aurawallet.app.fakes.ScriptedCategorizationRepository
import com.aurawallet.app.fakes.categorization
import com.aurawallet.app.fakes.failure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР5: прикладна логіка сценарію перевіряється без backend, Retrofit і Room. */
class CategorizeUseCaseTest {

    @Test
    fun successIsTrimmedSentWithLocaleAndSavedToHistory() = runBlocking {
        val repo = ScriptedCategorizationRepository(Result.success(categorization()))
        val history = InMemoryHistoryStore()
        val entry = CategorizeTransactionUseCase(repo, history)("  Кава в Aroma Kava 85 грн  ").getOrThrow()

        assertEquals("Кава в Aroma Kava 85 грн", repo.calls.single().text)
        assertEquals("uk", repo.calls.single().locale)
        assertEquals("coffee", entry.result.category)
        assertEquals(1L, entry.recordId)
        assertEquals("Кава в Aroma Kava 85 грн", history.done.single().first)
    }

    @Test
    fun invalidInputNeverReachesRepository() = runBlocking {
        val repo = ScriptedCategorizationRepository()
        val history = InMemoryHistoryStore()
        val useCase = CategorizeTransactionUseCase(repo, history)

        for (text in listOf("", "   ", "а", "12345", "к".repeat(201))) {
            val error = useCase(text).exceptionOrNull()!!.toAppError()
            assertTrue("'$text' → $error", error is AppError.Validation)
        }
        assertTrue(repo.calls.isEmpty())
        assertTrue(history.done.isEmpty())
    }

    @Test
    fun networkErrorIsReturnedAndNothingIsSaved() = runBlocking {
        val repo = ScriptedCategorizationRepository(failure(AppError.Network))
        val history = InMemoryHistoryStore()
        val error = CategorizeTransactionUseCase(repo, history)("таксі 180").exceptionOrNull()!!.toAppError()

        assertEquals(AppError.Network, error)
        assertTrue(error.isConnectivity)
        assertTrue("помилки не зберігаються (ЛР4)", history.done.isEmpty())
    }

    @Test
    fun serverErrorKeepsBackendMessage() = runBlocking {
        val repo = ScriptedCategorizationRepository(failure(AppError.Server("AI-сервіс тимчасово недоступний", "ai_unavailable")))
        val error = CategorizeTransactionUseCase(repo, InMemoryHistoryStore())("кава 85").exceptionOrNull()!!.toAppError()

        assertEquals("AI-сервіс тимчасово недоступний", error.userMessage)
        assertTrue(!error.isConnectivity)
    }

    @Test
    fun historyFailureDoesNotHideResult() = runBlocking {
        val repo = ScriptedCategorizationRepository(Result.success(categorization()))
        val entry = CategorizeTransactionUseCase(repo, InMemoryHistoryStore(failOnAdd = true))("кава 85").getOrThrow()

        assertEquals("coffee", entry.result.category)
        assertNull(entry.recordId)
    }
}
