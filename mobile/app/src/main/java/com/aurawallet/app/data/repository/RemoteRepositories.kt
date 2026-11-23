package com.aurawallet.app.data.repository

import com.aurawallet.app.data.mapper.budgetRequest
import com.aurawallet.app.data.mapper.toDomain
import com.aurawallet.app.data.mapper.toDto
import com.aurawallet.app.data.remote.AuraApi
import com.aurawallet.app.data.remote.safeApiCall
import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.SavedTransaction
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import java.time.LocalDate

/** Реальна реалізація: FastAPI backend через Retrofit. [api] передається ззовні (composition root або тест). */
class RemoteCategorizationRepository(private val api: AuraApi) : CategorizationRepository {
    override suspend fun categorize(input: CategorizationInput): Result<Categorization> =
        safeApiCall { api.categorize(input.toDto()).toDomain() }

    override suspend fun isAvailable(): Boolean =
        safeApiCall { api.health().status == "ok" }.getOrDefault(false)
}

/** Бюджет місяця (ЛР3) через `POST /api/v1/budget/summary`. */
class RemoteBudgetRepository(
    private val api: AuraApi,
    private val today: () -> LocalDate = LocalDate::now,
) : BudgetRepository {
    override suspend fun summary(monthlyBudget: Double, saved: List<SavedTransaction>): Result<BudgetSummary> =
        safeApiCall { api.budgetSummary(budgetRequest(monthlyBudget, saved, today().toString())).toDomain() }
}
