package com.aurawallet.app.data.mapper

import com.aurawallet.app.data.remote.BudgetSummaryRequestDto
import com.aurawallet.app.data.remote.BudgetSummaryResponseDto
import com.aurawallet.app.data.remote.BudgetTransactionDto
import com.aurawallet.app.data.remote.CategorizeRequestDto
import com.aurawallet.app.data.remote.CategorizeResponseDto
import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.SavedTransaction

/** Перетворення DTO ↔ доменні моделі. Єдине місце, яке знає і про JSON-формат, і про домен. */

fun CategorizationInput.toDto() = CategorizeRequestDto(text = text.trim(), locale = locale)

fun CategorizeResponseDto.toDomain() = Categorization(
    category = category,
    label = label,
    confidence = confidence.coerceIn(0.0, 1.0),
    isIncome = isIncome,
    amount = amount,
    currency = currency,
    merchant = merchant,
    explanation = explanation,
    alternatives = alternatives.map { Categorization.Alternative(it.category, it.label, it.confidence) },
    lowConfidence = lowConfidence,
    processingMs = processingMs,
)

fun BudgetSummaryResponseDto.toDomain() = BudgetSummary(
    monthlyBudget = monthlyBudget,
    spent = spent,
    remaining = remaining,
    percentUsed = percentUsed,
    status = when (status) {
        "exceeded" -> BudgetSummary.Status.Exceeded
        "warning" -> BudgetSummary.Status.Warning
        else -> BudgetSummary.Status.Ok
    },
    daysLeft = daysLeft,
    dailyAllowance = dailyAllowance,
    byCategory = byCategory.map { BudgetSummary.Slice(it.category, it.label, it.amount, it.share.coerceIn(0.0, 1.0)) },
    message = message,
)

/** Лише гривневі операції з сумою потрапляють у розрахунок бюджету (конвертації валют поки немає). */
fun List<SavedTransaction>.toBudgetItems(): List<BudgetTransactionDto> =
    filter { it.amount != null && it.amount > 0 && (it.currency == null || it.currency == "UAH") }
        .map { BudgetTransactionDto(amount = it.amount!!, category = it.category, isIncome = it.isIncome) }

fun budgetRequest(monthlyBudget: Double, saved: List<SavedTransaction>, today: String) =
    BudgetSummaryRequestDto(monthlyBudget = monthlyBudget, transactions = saved.toBudgetItems(), today = today)
