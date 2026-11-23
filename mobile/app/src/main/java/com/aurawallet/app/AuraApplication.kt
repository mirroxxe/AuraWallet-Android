package com.aurawallet.app

import android.app.Application
import com.aurawallet.app.data.local.AuraDatabase
import com.aurawallet.app.data.remote.AuraApi
import com.aurawallet.app.data.remote.Network
import com.aurawallet.app.data.repository.HistoryRepository
import com.aurawallet.app.data.repository.RemoteBudgetRepository
import com.aurawallet.app.data.repository.RemoteCategorizationRepository
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase

/**
 * Composition root: єдине місце, де обираються конкретні реалізації.
 * Решта коду отримує їх через конструктори як інтерфейси (рішення D8, D20).
 */
class AuraApplication : Application() {
    private val api: AuraApi by lazy { Network.api }

    val database: AuraDatabase by lazy { AuraDatabase.get(this) }
    val history: HistoryRepository by lazy { HistoryRepository(database.history()) }

    val categorization: CategorizationRepository by lazy { RemoteCategorizationRepository(api) }
    val budgets: BudgetRepository by lazy { RemoteBudgetRepository(api) }

    val categorizeUseCase: CategorizeTransactionUseCase by lazy { CategorizeTransactionUseCase(categorization, history) }
}
