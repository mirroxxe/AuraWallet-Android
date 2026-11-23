package com.aurawallet.app

import android.app.Application
import com.aurawallet.app.data.local.AuraDatabase
import com.aurawallet.app.data.remote.AuraApi
import com.aurawallet.app.data.remote.Network
import com.aurawallet.app.data.repository.FakeCategorizationRepository
import com.aurawallet.app.data.repository.HistoryRepository
import com.aurawallet.app.data.repository.RemoteBudgetRepository
import com.aurawallet.app.data.repository.RemoteCategorizationRepository
import com.aurawallet.app.data.repository.SwitchableCategorizationRepository
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.DataSourceSwitch
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase

/**
 * Composition root: єдине місце, де обираються конкретні реалізації.
 * Решта коду отримує їх через конструктори як інтерфейси (рішення D8, D20).
 */
class AuraApplication : Application() {
    private val api: AuraApi by lazy { Network.api }

    val database: AuraDatabase by lazy { AuraDatabase.get(this) }
    val history: HistoryRepository by lazy { HistoryRepository(database.history()) }

    /** ЛР5: real (FastAPI) і fake (демо) реалізації за одним інтерфейсом; перемикач лише в debug-збірці. */
    private val switchable: SwitchableCategorizationRepository by lazy {
        SwitchableCategorizationRepository(real = RemoteCategorizationRepository(api), demo = FakeCategorizationRepository())
    }
    val categorization: CategorizationRepository get() = switchable
    val dataSource: DataSourceSwitch get() = if (BuildConfig.DEBUG) switchable else DataSourceSwitch.Fixed
    val budgets: BudgetRepository by lazy { RemoteBudgetRepository(api) }

    val categorizeUseCase: CategorizeTransactionUseCase by lazy { CategorizeTransactionUseCase(categorization, history) }
}
