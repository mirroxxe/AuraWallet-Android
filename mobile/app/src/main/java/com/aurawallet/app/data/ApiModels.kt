package com.aurawallet.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тіло запиту POST /api/v1/transactions/categorize. */
@Serializable
data class CategorizeRequestDto(
    val text: String,
    val locale: String = "uk",
)

/** JSON-відповідь backend. Імена полів збігаються з Pydantic-моделлю CategorizeResponse. */
@Serializable
data class CategorizeResponseDto(
    val category: String,
    val label: String,
    val confidence: Double,
    @SerialName("is_income") val isIncome: Boolean,
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val explanation: String,
    val alternatives: List<AlternativeDto> = emptyList(),
    @SerialName("low_confidence") val lowConfidence: Boolean = false,
    @SerialName("processing_ms") val processingMs: Double = 0.0,
)

@Serializable
data class AlternativeDto(
    val category: String,
    val label: String,
    val confidence: Double,
)

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
    @SerialName("model_loaded") val modelLoaded: Boolean,
)

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(val code: String, val message: String, val field: String? = null)
