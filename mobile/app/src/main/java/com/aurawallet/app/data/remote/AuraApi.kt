package com.aurawallet.app.data.remote

import com.aurawallet.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface AuraApi {
    @GET("health")
    suspend fun health(): HealthDto

    @POST("api/v1/transactions/categorize")
    suspend fun categorize(@Body body: CategorizeRequestDto): CategorizeResponseDto

    @POST("api/v1/budget/summary")
    suspend fun budgetSummary(@Body body: BudgetSummaryRequestDto): BudgetSummaryResponseDto
}

object Network {
    /** Невідомі поля в JSON ігноруємо, щоб нова версія backend не ламала застосунок. */
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                if (BuildConfig.API_KEY.isNotBlank()) builder.header("X-API-Key", BuildConfig.API_KEY)
                chain.proceed(builder.build())
            }
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
            .build()
    }

    val api: AuraApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuraApi::class.java)
    }
}
