package com.aurawallet.app.domain.usecase

import com.aurawallet.app.domain.model.AppErrorException
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.HistoryStore
import kotlin.coroutines.cancellation.CancellationException

/** Успішний результат сценарію: що розпізнали і під яким id записали в історію. */
data class CategorizedEntry(val input: String, val result: Categorization, val recordId: Long?)

/**
 * Сценарій «розпізнати операцію»: валідація → репозиторій → запис успіху в історію.
 * Не знає ні про Retrofit, ні про Room, ні про Compose — лише про інтерфейси.
 */
class CategorizeTransactionUseCase(
    private val repository: CategorizationRepository,
    private val history: HistoryStore,
    private val locale: String = "uk",
) {
    suspend operator fun invoke(rawText: String): Result<CategorizedEntry> {
        val text = rawText.trim()
        InputValidator.validate(text)?.let { return Result.failure(AppErrorException(it)) }

        val result = repository.categorize(CategorizationInput(text, locale))
        return result.map { categorization ->
            // Збій запису в історію не повинен ховати від користувача готовий результат.
            val id = try {
                history.addDone(text, categorization)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            CategorizedEntry(text, categorization, id)
        }
    }
}
