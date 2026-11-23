package com.aurawallet.app

import com.aurawallet.app.data.repository.FakeCategorizationRepository
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.ConfidenceSource
import com.aurawallet.app.domain.model.PersonalExample
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase
import com.aurawallet.app.fakes.InMemoryHistoryStore
import com.aurawallet.app.fakes.ScriptedCategorizationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР6: персональний контекст іде від історії через use case до репозиторію. */
class PersonalizationTest {
    private val corrections = listOf(PersonalExample("Епіцентр фарба для стін 1200", "housing"))

    @Test
    fun correctionsAreSentWhenPersonalizationIsOn() = runBlocking {
        val repo = ScriptedCategorizationRepository()
        CategorizeTransactionUseCase(repo, InMemoryHistoryStore(corrections = corrections))("Епіцентр 640", personalize = true)
        assertEquals(corrections, repo.calls.single().personalExamples)
    }

    @Test
    fun nothingIsSentWhenPersonalizationIsOff() = runBlocking {
        val repo = ScriptedCategorizationRepository()
        CategorizeTransactionUseCase(repo, InMemoryHistoryStore(corrections = corrections))("Епіцентр 640", personalize = false)
        assertTrue(repo.calls.single().personalExamples.isEmpty())
    }

    @Test
    fun historyFailureFallsBackToPlainRequest() = runBlocking {
        val repo = ScriptedCategorizationRepository()
        val result = CategorizeTransactionUseCase(repo, InMemoryHistoryStore(corrections = corrections, failOnCorrections = true))("Епіцентр 640")
        assertTrue(result.isSuccess)
        assertTrue(repo.calls.single().personalExamples.isEmpty())
    }

    @Test
    fun atMostFiftyExamplesAreRequested() = runBlocking {
        val many = (1..80).map { PersonalExample("магазин $it", "shopping") }
        val repo = ScriptedCategorizationRepository()
        CategorizeTransactionUseCase(repo, InMemoryHistoryStore(corrections = many))("кава 85")
        assertEquals(50, repo.calls.single().personalExamples.size)
    }

    @Test
    fun fakeRepositoryHonoursPersonalContext() = runBlocking {
        val fake = FakeCategorizationRepository(delayMs = 0)
        val r = fake.categorize(CategorizationInput("Епіцентр фарба 300", personalExamples = corrections)).getOrThrow()
        assertEquals("housing", r.category)
        assertEquals("Житло", r.label)
        assertEquals(ConfidenceSource.Personal, r.confidenceSource)
        val plain = fake.categorize(CategorizationInput("Кава 85", personalExamples = corrections)).getOrThrow()
        assertEquals(ConfidenceSource.Model, plain.confidenceSource)
        assertNull(plain.personalization)
    }
}
