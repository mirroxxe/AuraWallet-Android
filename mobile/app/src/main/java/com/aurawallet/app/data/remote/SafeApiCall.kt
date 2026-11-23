package com.aurawallet.app.data.remote

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Межа між мережею і застосунком: будь-який технічний виняток стає визначеною категорією [AppError].
 * Вище цього рівня IOException, HttpException і SerializationException не проходять.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AppErrorException(e.toNetworkAppError()))
    }
}

fun Exception.toNetworkAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout
    is HttpException -> httpError(this)
    is SerializationException -> AppError.BadResponse
    is IllegalArgumentException -> AppError.BadResponse // напр. порожнє тіло або не той JSON
    is IOException -> AppError.Network
    else -> AppError.Unknown
}

private fun httpError(e: HttpException): AppError {
    val raw = e.response()?.errorBody()?.string().orEmpty()
    val body = runCatching { Network.json.decodeFromString(ErrorEnvelopeDto.serializer(), raw).error }.getOrNull()
    return when {
        body != null -> AppError.Server(body.message, body.code)
        e.code() >= 500 -> AppError.Server("Сервер тимчасово недоступний (${e.code()}). Спробуйте пізніше.", "http_${e.code()}")
        else -> AppError.Server("Запит відхилено сервером (${e.code()}).", "http_${e.code()}")
    }
}
