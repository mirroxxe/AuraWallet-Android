package com.aurawallet.app

import com.aurawallet.app.data.repository.FakeCategorizationRepository
import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.toAppError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР5: друга реалізація CategorizationRepository поводиться за тим самим контрактом. */
class FakeRepositoryTest {
    private val repo = FakeCategorizationRepository(delayMs = 0)
    private fun run(text: String) = runBlocking { repo.categorize(CategorizationInput(text)) }

    @Test
    fun recognizesCoffeeAmountAndCurrency() {
        val r = run("Кава в Aroma Kava 85 грн").getOrThrow()
        assertEquals("coffee", r.category)
        assertEquals(85.0, r.amount!!, 0.0)
        assertEquals("UAH", r.currency)
    }

    @Test
    fun incomeAndForeignCurrency() {
        assertTrue(run("Зарплата 30000").getOrThrow().isIncome)
        assertEquals("USD", run("Spotify підписка $5").getOrThrow().currency)
    }

    @Test
    fun unknownTextIsLowConfidenceOther() {
        val r = run("щось незрозуміле xyz").getOrThrow()
        assertEquals("other", r.category)
        assertTrue(r.lowConfidence)
    }

    @Test
    fun simulatedErrorIsControlledAppError() {
        val e = run("помилка 10").exceptionOrNull()!!.toAppError()
        assertTrue(e is AppError.Server)
    }
}
