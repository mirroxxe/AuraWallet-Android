package com.aurawallet.app

import android.app.Application
import com.aurawallet.app.data.HistoryRepository
import com.aurawallet.app.data.TransactionRepository
import com.aurawallet.app.data.local.AuraDatabase
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase

/**
 * Composition root: єдине місце, де обираються конкретні реалізації.
 * Решта коду отримує їх через конструктори як інтерфейси (рішення D8, D20).
 */
class AuraApplication : Application() {
    val database: AuraDatabase by lazy { AuraDatabase.get(this) }
    val history: HistoryRepository by lazy { HistoryRepository(database.history()) }

    private val remote: TransactionRepository by lazy { TransactionRepository() }
    val categorization: CategorizationRepository get() = remote
    val budgets: BudgetRepository get() = remote

    val categorizeUseCase: CategorizeTransactionUseCase by lazy { CategorizeTransactionUseCase(categorization, history) }
}
