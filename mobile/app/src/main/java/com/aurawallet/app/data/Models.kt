package com.aurawallet.app.data

/** Внутрішня модель результату, яку бачить UI (не залежить від формату JSON). */
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

/** Помилки, які UI вміє показати зрозуміло. */
sealed class AppError(val userMessage: String) {
    data object Network : AppError("Немає з'єднання з сервером. Перевірте, що backend запущено, і спробуйте ще раз.")
    data object Timeout : AppError("Сервер не відповів вчасно. Спробуйте ще раз.")
    data object BadResponse : AppError("Сервер повернув неочікувану відповідь. Спробуйте ще раз.")
    class Server(message: String, val code: String) : AppError(message)
}

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
