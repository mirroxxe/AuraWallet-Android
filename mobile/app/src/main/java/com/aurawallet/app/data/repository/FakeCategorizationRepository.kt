package com.aurawallet.app.data.repository

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.AppErrorException
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.ConfidenceSource
import com.aurawallet.app.domain.model.Personalization
import com.aurawallet.app.domain.repository.CategorizationRepository
import kotlinx.coroutines.delay

/**
 * Друга реалізація [CategorizationRepository]: без мережі й AI, за ключовими словами.
 * Для демо-режиму і ручних перевірок без backend. Слово «помилка» в тексті імітує збій сервера.
 */
class FakeCategorizationRepository(private val delayMs: Long = 400) : CategorizationRepository {

    private class Rule(val category: String, val label: String, val keywords: List<String>, val income: Boolean = false)

    private val rules = listOf(
        Rule("coffee", "Кава", listOf("кава", "кав", "coffee", "лате", "капучино")),
        Rule("cafes", "Кафе", listOf("кафе", "ресторан", "піца", "суші", "макдональдс", "mcdonald")),
        Rule("transport", "Транспорт", listOf("таксі", "uklon", "uber", "bolt", "метро", "автобус", "бензин")),
        Rule("groceries", "Продукти", listOf("сільпо", "атб", "novus", "фора", "продукти")),
        Rule("subscriptions", "Підписки", listOf("netflix", "spotify", "youtube", "підписка")),
        Rule("mobile", "Зв'язок", listOf("київстар", "vodafone", "lifecell")),
        Rule("health", "Здоров'я", listOf("аптека", "лікар", "стоматолог")),
        Rule("shopping", "Покупки", listOf("rozetka", "розетка", "навушники")),
        Rule("entertainment", "Розваги", listOf("кіно", "концерт", "театр")),
        Rule("transfers", "Перекази", listOf("переказ")),
        Rule("salary", "Зарплата", listOf("зарплата", "зп"), income = true),
        Rule("freelance", "Фриланс", listOf("фриланс", "upwork", "fiverr"), income = true),
    )

    /** Підписи категорій, яких немає серед правил (для персоналізованого результату). */
    private val extraLabels = mapOf(
        "housing" to "Житло", "gifts" to "Подарунки", "clothing" to "Одяг", "travel" to "Подорожі",
        "cashback" to "Кешбек", "other" to "Інше",
    )

    private fun labelOf(category: String) = rules.firstOrNull { it.category == category }?.label ?: extraLabels[category] ?: category

    private val number = Regex("""(\d+(?:[.,]\d+)?)""")

    override suspend fun categorize(input: CategorizationInput): Result<Categorization> {
        delay(delayMs)
        val text = input.text.lowercase()
        if ("помилка" in text) {
            return Result.failure(AppErrorException(AppError.Server("Демо-режим: імітація збою сервера.", "demo_error")))
        }
        val rule = rules.firstOrNull { r -> r.keywords.any { it in text } }
        val amount = number.findAll(text).lastOrNull()?.value?.replace(',', '.')?.toDoubleOrNull()
        val currency = when {
            amount == null -> null
            "$" in text || "usd" in text || "дол" in text -> "USD"
            "€" in text || "eur" in text || "євро" in text -> "EUR"
            else -> "UAH"
        }
        val keyword = rule?.keywords?.first { it in text }
        personalMatch(text, input)?.let { (example, similarity) ->
            val modelCategory = rule?.category ?: "other"
            if (example.category != modelCategory || rule == null) {
                return Result.success(
                    Categorization(
                        category = example.category, label = labelOf(example.category), confidence = similarity, isIncome = false,
                        amount = amount, currency = currency, merchant = null,
                        explanation = "Демо-режим: ви раніше віднесли «${example.text}» до цієї категорії.",
                        alternatives = listOfNotNull(rule?.let { Categorization.Alternative(it.category, it.label, 0.9) }),
                        lowConfidence = false, processingMs = 0.0, confidenceSource = ConfidenceSource.Personal,
                        personalization = Personalization(example.text, example.category, similarity, modelCategory, rule?.label ?: "Інше"),
                    ),
                )
            }
        }
        return Result.success(
            Categorization(
                category = rule?.category ?: "other",
                label = rule?.label ?: "Інше",
                confidence = if (rule != null) 0.9 else 0.2,
                isIncome = rule?.income ?: false,
                amount = amount,
                currency = currency,
                merchant = null,
                explanation = if (keyword != null) "Демо-режим: знайдено слово «$keyword»." else "Демо-режим: ключових слів не знайдено, оберіть категорію.",
                alternatives = emptyList(),
                lowConfidence = rule == null,
                processingMs = 0.0,
            ),
        )
    }

    /** Спрощена персоналізація для демо: частка слів прикладу (без чисел), які є в запиті. */
    private fun personalMatch(text: String, input: CategorizationInput) = input.personalExamples
        .map { ex ->
            val words = ex.text.lowercase().split(' ').filter { w -> w.length >= 3 && w.any(Char::isLetter) }
            ex to if (words.isEmpty()) 0.0 else words.count { it in text }.toDouble() / words.size
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second >= 0.5 }

    override suspend fun isAvailable(): Boolean = true
}
