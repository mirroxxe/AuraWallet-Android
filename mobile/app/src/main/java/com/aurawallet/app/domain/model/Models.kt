package com.aurawallet.app.domain.model

/** Що саме просимо розпізнати. Не залежить від формату запиту до backend. */
data class CategorizationInput(
    val text: String,
    val locale: String = "uk",
    /** ЛР6: попередні виправлення користувача, новіші першими (додатковий контекст для AI). */
    val personalExamples: List<PersonalExample> = emptyList(),
)

/** ЛР6: операція, категорію якої користувач раніше змінив вручну. */
data class PersonalExample(val text: String, val category: String)

/** Звідки береться число confidence. */
enum class ConfidenceSource {
    /** ймовірність класу від моделі */
    Model,
    /** косинусна схожість із виправленням користувача — не ймовірність правильності */
    Personal,
}

/** ЛР6: чим персоналізовано результат. */
data class Personalization(
    val matchedText: String,
    val matchedCategory: String,
    val similarity: Double,
    val modelCategory: String,
    val modelLabel: String,
)

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
    val confidenceSource: ConfidenceSource = ConfidenceSource.Model,
    val personalization: Personalization? = null,
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
