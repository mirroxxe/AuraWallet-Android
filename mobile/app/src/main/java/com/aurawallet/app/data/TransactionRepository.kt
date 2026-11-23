package com.aurawallet.app.data

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.AppErrorException
import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.SavedTransaction
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Шар даних: викликає API і перетворює будь-які збої на зрозумілі AppError. */
class TransactionRepository(private val api: AuraApi = Network.api) : CategorizationRepository, BudgetRepository {

    override suspend fun categorize(input: CategorizationInput): Result<Categorization> = call {
        api.categorize(CategorizeRequestDto(text = input.text.trim(), locale = input.locale)).toDomain()
    }

    override suspend fun summary(monthlyBudget: Double, saved: List<SavedTransaction>): Result<BudgetSummary> = call {
        api.budgetSummary(
            BudgetSummaryRequestDto(
                monthlyBudget = monthlyBudget,
                transactions = saved.toBudgetItems(),
                today = java.time.LocalDate.now().toString(),
            ),
        ).toDomain()
    }

    override suspend fun isAvailable(): Boolean = call { api.health().status == "ok" }.getOrDefault(false)

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: SocketTimeoutException) {
            Result.failure(AppErrorException(AppError.Timeout))
        } catch (e: HttpException) {
            Result.failure(AppErrorException(parseHttpError(e)))
        } catch (e: SerializationException) {
            Result.failure(AppErrorException(AppError.BadResponse))
        } catch (e: IllegalArgumentException) { // напр. порожнє тіло або не той JSON
            Result.failure(AppErrorException(AppError.BadResponse))
        } catch (e: IOException) {
            Result.failure(AppErrorException(AppError.Network))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // P4: непередбачене — не «немає з'єднання», а нейтральна помилка
            Result.failure(AppErrorException(AppError.Unknown))
        }
    }

    private fun parseHttpError(e: HttpException): AppError {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        val body = runCatching { Network.json.decodeFromString<ErrorEnvelopeDto>(raw).error }.getOrNull()
        return when {
            body != null -> AppError.Server(body.message, body.code)
            e.code() >= 500 -> AppError.Server("Сервер тимчасово недоступний (${e.code()}). Спробуйте пізніше.", "http_${e.code()}")
            else -> AppError.Server("Запит відхилено сервером (${e.code()}).", "http_${e.code()}")
        }
    }
}
