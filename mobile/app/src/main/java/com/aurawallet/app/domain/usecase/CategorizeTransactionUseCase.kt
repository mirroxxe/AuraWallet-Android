package com.aurawallet.app.domain.usecase

import com.aurawallet.app.domain.model.AppErrorException
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.PersonalExample
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.HistoryStore
import kotlin.coroutines.cancellation.CancellationException

/** Успішний результат сценарію: що розпізнали і під яким id записали в історію. */
data class CategorizedEntry(val input: String, val result: Categorization, val recordId: Long?)

/**
 * Сценарій «розпізнати операцію»: валідація → (ЛР6) персональний контекст → репозиторій → запис успіху в історію.
 * Не знає ні про Retrofit, ні про Room, ні про Compose — лише про інтерфейси.
 */
class CategorizeTransactionUseCase(
    private val repository: CategorizationRepository,
    private val history: HistoryStore,
    private val locale: String = "uk",
) {
    /** [personalize] — чи надсилати виправлення користувача як контекст (перемикач в UI). */
    suspend operator fun invoke(rawText: String, personalize: Boolean = true): Result<CategorizedEntry> {
        val text = rawText.trim()
        InputValidator.validate(text)?.let { return Result.failure(AppErrorException(it)) }

        val examples = if (personalize) loadExamples() else emptyList()
        val result = repository.categorize(CategorizationInput(text, locale, examples))
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

    /** Збій читання історії не блокує розпізнавання: тоді просто без персоналізації. */
    private suspend fun loadExamples(): List<PersonalExample> = try {
        history.personalExamples(MAX_PERSONAL_EXAMPLES)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        /** Обмеження API: не більше 50 прикладів у запиті. */
        const val MAX_PERSONAL_EXAMPLES = 50
    }
}
