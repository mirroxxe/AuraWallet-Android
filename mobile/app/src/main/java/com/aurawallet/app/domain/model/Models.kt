package com.aurawallet.app.domain.model

/** Що саме просимо розпізнати. Не залежить від формату запиту до backend. */
data class CategorizationInput(val text: String, val locale: String = "uk")

/** Результат розпізнавання, який бачить застосунок (не залежить від формату JSON). */
data class Categorization(
    val category: String,
    val label: String,
    val confidence: Double,
    val isIncome: Boolean,
    val amount: Double?,
    val currency: String?,
    val merchant: String?,
    val explanation: String,
    val alternatives: List<Alternative>,
    val lowConfidence: Boolean,
    val processingMs: Double,
) {
    data class Alternative(val category: String, val label: String, val confidence: Double)
}

/** Операція, яку користувач підтвердив і зберіг. */
data class SavedTransaction(
    val id: Long,
    val text: String,
    val category: String,
    val label: String,
    val amount: Double?,
    val currency: String?,
    val isIncome: Boolean,
)

/** Підсумок бюджету місяця. */
data class BudgetSummary(
    val monthlyBudget: Double,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Double,
    val status: Status,
    val daysLeft: Int,
    val dailyAllowance: Double,
    val byCategory: List<Slice>,
    val message: String,
) {
    enum class Status { Ok, Warning, Exceeded }
    data class Slice(val category: String, val label: String, val amount: Double, val share: Double)
}
