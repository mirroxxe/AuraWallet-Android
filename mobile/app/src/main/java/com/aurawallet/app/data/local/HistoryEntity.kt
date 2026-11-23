package com.aurawallet.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Рядок локальної історії (ЛР4). Для статусу PENDING поля результату порожні.
 * Не зберігаються: тексти помилок, HTTP-коди, ключі, адреса сервера.
 */
@Entity(tableName = "history", indices = [Index("created_at"), Index("confirmed")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "input_text") val inputText: String,
    val status: String,
    val category: String? = null,
    val label: String? = null,
    val confidence: Double? = null,
    @ColumnInfo(name = "is_income") val isIncome: Boolean = false,
    @ColumnInfo(name = "low_confidence") val lowConfidence: Boolean = false,
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val explanation: String? = null,
    /** JSON-масив альтернатив [{category,label,confidence}]. */
    val alternatives: String? = null,
    @ColumnInfo(name = "processing_ms") val processingMs: Double? = null,
    /** Користувач натиснув «Зберегти» — операція бере участь у бюджеті. */
    val confirmed: Boolean = false,
    @ColumnInfo(name = "chosen_category") val chosenCategory: String? = null,
    @ColumnInfo(name = "chosen_label") val chosenLabel: String? = null,
)
