package com.aurawallet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Шар даних: викликає API і перетворює будь-які збої на зрозумілі AppError. */
class TransactionRepository(private val api: AuraApi = Network.api) {

    suspend fun categorize(text: String): Result<Categorization> = call {
        api.categorize(CategorizeRequestDto(text = text.trim())).toDomain()
    }

    suspend fun budgetSummary(budget: Double, saved: List<SavedTransaction>): Result<BudgetSummary> = call {
        api.budgetSummary(
            BudgetSummaryRequestDto(
                monthlyBudget = budget,
                transactions = saved.toBudgetItems(),
                today = java.time.LocalDate.now().toString(),
            ),
        ).toDomain()
    }

    suspend fun isServerUp(): Boolean = call { api.health().status == "ok" }.getOrDefault(false)

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

class AppErrorException(val error: AppError) : Exception(error.userMessage)

fun Throwable.toAppError(): AppError = (this as? AppErrorException)?.error ?: AppError.Network
