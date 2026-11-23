package com.aurawallet.app

import com.aurawallet.app.data.Categorization
import com.aurawallet.app.data.HistoryStatus
import com.aurawallet.app.data.local.HistoryEntity
import com.aurawallet.app.data.toEntity
import com.aurawallet.app.data.toRecord
import com.aurawallet.app.data.toSavedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР4: мапінг результату ↔ рядок Room без емулятора. */
class HistoryMappingTest {
    private val result = Categorization(
        category = "coffee", label = "Кава", confidence = 0.95, isIncome = false, amount = 85.0, currency = "UAH",
        merchant = "Aroma Kava", explanation = "Схоже на «Кава»",
        alternatives = listOf(Categorization.Alternative("cafes", "Кафе", 0.03)), lowConfidence = false, processingMs = 3.0,
    )

    @Test
    fun roundTripKeepsResult() {
        val entity = result.toEntity("Кава в Aroma Kava 85 грн", now = 1_000L).copy(id = 7)
        val record = entity.toRecord()
        assertEquals(HistoryStatus.DONE, record.status)
        assertEquals(result, record.result)
        assertEquals(1_000L, record.createdAt)
        assertEquals("Кава в Aroma Kava 85 грн", record.inputText)
    }

    @Test
    fun pendingHasNoResult() {
        val record = HistoryEntity(id = 1, createdAt = 1L, inputText = "таксі 180", status = "PENDING").toRecord()
        assertEquals(HistoryStatus.PENDING, record.status)
        assertNull(record.result)
        assertNull(record.toSavedTransaction())
    }

    @Test
    fun chosenCategoryOverridesAiForBudget() {
        val entity = result.toEntity("кава", 1L).copy(id = 3, confirmed = true, chosenCategory = "cafes", chosenLabel = "Кафе")
        val tx = entity.toRecord().toSavedTransaction()!!
        assertEquals("cafes", tx.category)
        assertEquals(85.0, tx.amount!!, 0.0)
    }

    @Test
    fun brokenAlternativesJsonDoesNotCrash() {
        val record = result.toEntity("кава", 1L).copy(alternatives = "{broken").toRecord()
        assertTrue(record.result!!.alternatives.isEmpty())
    }

    @Test
    fun unknownStatusTreatedAsPending() {
        assertEquals(HistoryStatus.PENDING, HistoryEntity(createdAt = 1, inputText = "x", status = "WHAT").toRecord().status)
    }
}
