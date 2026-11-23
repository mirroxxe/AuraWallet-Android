package com.aurawallet.app

import com.aurawallet.app.data.AppError
import com.aurawallet.app.data.AuraApi
import com.aurawallet.app.data.BudgetSummaryRequestDto
import com.aurawallet.app.data.BudgetSummaryResponseDto
import com.aurawallet.app.data.CategorizeRequestDto
import com.aurawallet.app.data.CategorizeResponseDto
import com.aurawallet.app.data.HealthDto
import com.aurawallet.app.data.TransactionRepository
import com.aurawallet.app.data.toAppError
import com.aurawallet.app.ui.CategorizeViewModel
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
        runBlocking { TransactionRepository(ThrowingApi(error)).categorize("кава 85").exceptionOrNull()!!.toAppError() }

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
    fun validationMessagesAreStable() {
        assertEquals("Введіть опис операції", CategorizeViewModel.validate("  "))
        assertEquals("Опис занадто короткий", CategorizeViewModel.validate("а"))
        assertEquals("Додайте хоча б одне слово, напр. «кава 85»", CategorizeViewModel.validate("12345"))
        assertEquals(null, CategorizeViewModel.validate("Кава в Aroma Kava 85 грн"))
    }
}
