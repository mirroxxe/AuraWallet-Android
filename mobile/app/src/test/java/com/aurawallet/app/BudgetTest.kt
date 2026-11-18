package com.aurawallet.app

import com.aurawallet.app.data.BudgetSummary
import com.aurawallet.app.data.BudgetSummaryResponseDto
import com.aurawallet.app.data.Network
import com.aurawallet.app.data.SavedTransaction
import com.aurawallet.app.data.toBudgetItems
import com.aurawallet.app.data.toDomain
import com.aurawallet.app.ui.CategorizeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ЛР3: розбір відповіді бюджету, відбір операцій, валідація ліміту. */
class BudgetTest {

    @Test
    fun parsesBudgetResponse() {
        val json = """{"monthly_budget":100,"spent":85,"income":0,"remaining":15,"percent_used":85.0,"status":"warning",
            "days_left":13,"daily_allowance":1.15,"by_category":[{"category":"coffee","label":"Кава","amount":85,"share":1}],
            "message":"Використано 80% бюджету","counted_transactions":1}"""
        val b = Network.json.decodeFromString(BudgetSummaryResponseDto.serializer(), json).toDomain()
        assertEquals(BudgetSummary.Status.Warning, b.status)
        assertEquals(1, b.byCategory.size)
    }

    @Test
    fun onlyUahWithAmountIsSent() {
        val saved = listOf(
            SavedTransaction(1, "кава 85", "coffee", "Кава", 85.0, "UAH", false),
            SavedTransaction(2, "Rozetka $45", "shopping", "Покупки", 45.0, "USD", false),
            SavedTransaction(3, "переказ", "transfers", "Перекази", null, null, false),
            SavedTransaction(4, "зарплата", "salary", "Зарплата", 38000.0, "UAH", true),
        )
        val items = saved.toBudgetItems()
        assertEquals(listOf("coffee", "salary"), items.map { it.category })
        assertEquals(true, items.last().isIncome)
    }

    @Test
    fun budgetValidation() {
        assertEquals(18000.0, CategorizeViewModel.parseBudget("18000")!!, 0.0)
        assertEquals(1500.5, CategorizeViewModel.parseBudget("1500,5")!!, 0.0)
        assertNull(CategorizeViewModel.parseBudget("0"))
        assertNull(CategorizeViewModel.parseBudget(""))
        assertNull(CategorizeViewModel.parseBudget("abc"))
    }
}
