package com.aurawallet.app

import com.aurawallet.app.data.remote.AuraApi
import com.aurawallet.app.data.remote.BudgetSummaryRequestDto
import com.aurawallet.app.data.remote.BudgetSummaryResponseDto
import com.aurawallet.app.data.remote.CategorizeRequestDto
import com.aurawallet.app.data.remote.CategorizeResponseDto
import com.aurawallet.app.data.remote.HealthDto
import com.aurawallet.app.data.repository.RemoteCategorizationRepository
import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.toAppError
import com.aurawallet.app.domain.usecase.InputValidator
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * ЛР5, крок 2: фіксуємо поточну поведінку сценарію розпізнавання ПЕРЕД рефакторингом.
 * Backend не потрібен: AuraApi підміняється об'єктом, який кидає потрібний виняток.
 */
class RegressionTest {

    private class ThrowingApi(private val error: Throwable) : AuraApi {
        override suspend fun health(): HealthDto = throw error
        override suspend fun categorize(body: CategorizeRequestDto): CategorizeResponseDto = throw error
        override suspend fun budgetSummary(body: BudgetSummaryRequestDto): BudgetSummaryResponseDto = throw error
    }

    private fun categorizeWith(error: Throwable): AppError =
        runBlocking { RemoteCategorizationRepository(ThrowingApi(error)).categorize(CategorizationInput("кава 85")).exceptionOrNull()!!.toAppError() }

    private fun http(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun timeoutBecomesTimeout() = assertEquals(AppError.Timeout, categorizeWith(SocketTimeoutException()))

    @Test
    fun ioErrorBecomesNetwork() = assertEquals(AppError.Network, categorizeWith(IOException("refused")))

    @Test
    fun backendErrorEnvelopeKeepsMessage() {
        val e = categorizeWith(http(503, """{"error":{"code":"ai_unavailable","message":"AI-сервіс тимчасово недоступний"}}"""))
        assertTrue(e is AppError.Server)
        assertEquals("AI-сервіс тимчасово недоступний", e.userMessage)
        assertEquals("ai_unavailable", (e as AppError.Server).code)
    }

    @Test
    fun http500WithoutBodyGetsGenericServerMessage() {
        val e = categorizeWith(http(500, "oops"))
        assertTrue(e.userMessage.contains("500"))
    }

    @Test
    fun unexpectedExceptionIsUnknownNotNetwork() = assertEquals(AppError.Unknown, categorizeWith(IllegalStateException("bug")))

    @Test
    fun validationMessagesAreStable() {
        assertEquals("Введіть опис операції", InputValidator.validate("  ")?.userMessage)
        assertEquals("Опис занадто короткий", InputValidator.validate("а")?.userMessage)
        assertEquals("Додайте хоча б одне слово, напр. «кава 85»", InputValidator.validate("12345")?.userMessage)
        assertEquals(null, InputValidator.validate("Кава в Aroma Kava 85 грн")?.userMessage)
    }
}
