package com.aurawallet.app

import com.aurawallet.app.data.repository.FakeCategorizationRepository
import com.aurawallet.app.data.repository.SwitchableCategorizationRepository
import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.toAppError
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase
import com.aurawallet.app.fakes.InMemoryHistoryStore
import com.aurawallet.app.fakes.ScriptedCategorizationRepository
import com.aurawallet.app.fakes.failure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР5, контрольне розширення: перемикання real ↔ fake без зміни use case. */
class SwitchableRepositoryTest {
    private val real = ScriptedCategorizationRepository(listOf(failure(AppError.Network), failure(AppError.Network))).apply { available = false }
    private val switch = SwitchableCategorizationRepository(real = real, demo = FakeCategorizationRepository(delayMs = 0))

    @Test
    fun startsWithRealSource() = runBlocking {
        assertFalse(switch.isDemo.value)
        assertFalse(switch.isAvailable())
        assertEquals(AppError.Network, switch.categorize(CategorizationInput("кава 85")).exceptionOrNull()!!.toAppError())
        assertEquals(1, real.calls.size)
    }

    @Test
    fun demoModeUsesFakeAndDoesNotTouchReal() = runBlocking {
        switch.setDemo(true)
        assertTrue(switch.isDemo.value)
        assertTrue(switch.isAvailable())
        assertEquals("coffee", switch.categorize(CategorizationInput("кава 85")).getOrThrow().category)
        assertTrue(real.calls.isEmpty())
    }

    @Test
    fun sameUseCaseWorksWithBothSources() = runBlocking {
        val history = InMemoryHistoryStore()
        val useCase = CategorizeTransactionUseCase(switch, history)
        assertTrue(useCase("таксі 180").isFailure)
        switch.setDemo(true)
        assertEquals("transport", useCase("таксі 180").getOrThrow().result.category)
        assertEquals(1, history.done.size)
    }
}
