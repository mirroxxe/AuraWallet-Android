package com.aurawallet.app.fakes

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.AppErrorException
import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.SavedTransaction
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.HistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Синтетичний результат для тестів. */
fun categorization(category: String = "coffee", label: String = "Кава", amount: Double? = 85.0) = Categorization(
    category = category, label = label, confidence = 0.95, isIncome = false, amount = amount, currency = "UAH",
    merchant = "Aroma Kava", explanation = "тест", alternatives = emptyList(), lowConfidence = false, processingMs = 1.0,
)

/** Керований fake: повертає заздалегідь задані відповіді й запам'ятовує виклики. */
class ScriptedCategorizationRepository(responses: List<Result<Categorization>> = emptyList()) : CategorizationRepository {
    constructor(single: Result<Categorization>) : this(listOf(single))

    private val queue = ArrayDeque(responses)
    val calls = mutableListOf<CategorizationInput>()
    var available = true

    override suspend fun categorize(input: CategorizationInput): Result<Categorization> {
        calls += input
        return queue.removeFirstOrNull() ?: Result.success(categorization())
    }

    override suspend fun isAvailable(): Boolean = available
}

fun failure(error: AppError): Result<Categorization> = Result.failure(AppErrorException(error))

/** Історія в пам'яті замість Room. */
class InMemoryHistoryStore(private val failOnAdd: Boolean = false) : HistoryStore {
    val done = mutableListOf<Pair<String, Categorization>>()
    val pending = mutableListOf<String>()
    private val confirmed = MutableStateFlow<List<SavedTransaction>>(emptyList())

    override fun observeConfirmed(): Flow<List<SavedTransaction>> = confirmed

    override suspend fun addDone(input: String, result: Categorization): Long {
        if (failOnAdd) throw IllegalStateException("disk full")
        done += input to result
        return done.size.toLong()
    }

    override suspend fun addPending(input: String): Long {
        pending += input
        return 100L + pending.size
    }

    override suspend fun confirm(id: Long, category: String, label: String) {
        val (text, r) = done[(id - 1).toInt()]
        confirmed.value = listOf(SavedTransaction(id, text, category, label, r.amount, r.currency, r.isIncome)) + confirmed.value
    }
}

/** Бюджет без backend: просто підсумовує суми. */
class FakeBudgetRepository : BudgetRepository {
    var calls = 0
    override suspend fun summary(monthlyBudget: Double, saved: List<SavedTransaction>): Result<BudgetSummary> {
        calls++
        val spent = saved.sumOf { it.amount ?: 0.0 }
        return Result.success(
            BudgetSummary(monthlyBudget, spent, monthlyBudget - spent, spent / monthlyBudget * 100, BudgetSummary.Status.Ok, 10, 0.0, emptyList(), ""),
        )
    }
}
