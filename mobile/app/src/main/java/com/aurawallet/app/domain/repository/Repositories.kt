package com.aurawallet.app.domain.repository

import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.SavedTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Джерело розпізнавання операцій. Реалізації: RemoteCategorizationRepository (FastAPI)
 * і FakeCategorizationRepository (локальні правила, без мережі).
 * Помилка завжди повертається як `Result.failure(AppErrorException(AppError))`, без технічних винятків.
 */
interface CategorizationRepository {
    suspend fun categorize(input: CategorizationInput): Result<Categorization>

    /** Чи доступне джерело зараз (для індикатора в заголовку). */
    suspend fun isAvailable(): Boolean
}

/** Розрахунок бюджету місяця (ЛР3). */
interface BudgetRepository {
    suspend fun summary(monthlyBudget: Double, saved: List<SavedTransaction>): Result<BudgetSummary>
}

/** Те, що сценарію розпізнавання потрібно від локальної історії (ЛР4). */
interface HistoryStore {
    fun observeConfirmed(): Flow<List<SavedTransaction>>
    suspend fun addDone(input: String, result: Categorization): Long
    suspend fun addPending(input: String): Long
    suspend fun confirm(id: Long, category: String, label: String)
}
