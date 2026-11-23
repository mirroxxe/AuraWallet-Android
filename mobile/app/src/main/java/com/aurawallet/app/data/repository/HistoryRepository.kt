package com.aurawallet.app.data.repository

import com.aurawallet.app.data.local.HistoryDao
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.SavedTransaction
import com.aurawallet.app.domain.repository.HistoryStore
import com.aurawallet.app.data.local.HistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Статуси запису: готово / очікує / надсилається. */
enum class HistoryStatus { DONE, PENDING, SENDING }

/** Доменна модель запису історії для UI. */
data class HistoryRecord(
    val id: Long,
    val createdAt: Long,
    val inputText: String,
    val status: HistoryStatus,
    val result: Categorization?,
    val confirmed: Boolean,
    val chosenCategory: String?,
    val chosenLabel: String?,
) {
    /** Що показувати в списку: обрана користувачем категорія або результат AI. */
    val displayLabel: String? get() = chosenLabel ?: result?.label
    val displayCategory: String? get() = chosenCategory ?: result?.category
}

@Serializable
private data class StoredAlternative(val category: String, val label: String, val confidence: Double)

private val altSerializer = ListSerializer(StoredAlternative.serializer())

/** Власний Json для поля alternatives: локальне сховище не залежить від мережевого шару. */
private val storageJson = Json { ignoreUnknownKeys = true }

/** Перетворення готового результату в рядок БД. Помилки сюди не потрапляють за типом: лише [Categorization]. */
fun Categorization.toEntity(input: String, now: Long): HistoryEntity = HistoryEntity(
    createdAt = now,
    inputText = input,
    status = HistoryStatus.DONE.name,
    category = category,
    label = label,
    confidence = confidence,
    isIncome = isIncome,
    lowConfidence = lowConfidence,
    amount = amount,
    currency = currency,
    merchant = merchant,
    explanation = explanation,
    alternatives = storageJson.encodeToString(altSerializer, alternatives.map { StoredAlternative(it.category, it.label, it.confidence) }),
    processingMs = processingMs,
)

fun HistoryEntity.toRecord(): HistoryRecord {
    val st = runCatching { HistoryStatus.valueOf(status) }.getOrDefault(HistoryStatus.PENDING)
    val result = if (st == HistoryStatus.DONE && category != null && label != null) {
        Categorization(
            category = category,
            label = label,
            confidence = confidence ?: 0.0,
            isIncome = isIncome,
            amount = amount,
            currency = currency,
            merchant = merchant,
            explanation = explanation.orEmpty(),
            alternatives = alternatives?.let { raw ->
                runCatching { storageJson.decodeFromString(altSerializer, raw) }.getOrDefault(emptyList())
                    .map { Categorization.Alternative(it.category, it.label, it.confidence) }
            }.orEmpty(),
            lowConfidence = lowConfidence,
            processingMs = processingMs ?: 0.0,
        )
    } else null
    return HistoryRecord(id, createdAt, inputText, st, result, confirmed, chosenCategory, chosenLabel)
}

fun HistoryRecord.toSavedTransaction(): SavedTransaction? {
    val r = result ?: return null
    return SavedTransaction(
        id = id, text = inputText, category = chosenCategory ?: r.category, label = chosenLabel ?: r.label,
        amount = r.amount, currency = r.currency, isIncome = r.isIncome,
    )
}

/** Операції над локальною історією, відокремлені від UI. */
class HistoryRepository(private val dao: HistoryDao, private val clock: () -> Long = System::currentTimeMillis) : HistoryStore {

    fun observeAll(): Flow<List<HistoryRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    fun observe(id: Long): Flow<HistoryRecord?> = dao.observeOne(id).map { it?.toRecord() }

    /** Підтверджені користувачем операції — джерело для бюджету (ЛР3). */
    override fun observeConfirmed(): Flow<List<SavedTransaction>> =
        dao.observeConfirmed().map { list -> list.mapNotNull { it.toRecord().toSavedTransaction() } }

    /** Зберігає лише успішний результат AI зі статусом «готово». Повертає id запису. */
    override suspend fun addDone(input: String, result: Categorization): Long = dao.insert(result.toEntity(input, clock()))

    /** Підвищений рівень: запит без мережі зі статусом «очікує» (лише текст запиту). */
    override suspend fun addPending(input: String): Long =
        dao.insert(HistoryEntity(createdAt = clock(), inputText = input, status = HistoryStatus.PENDING.name))

    override suspend fun confirm(id: Long, category: String, label: String) {
        val e = dao.get(id) ?: return
        dao.update(e.copy(confirmed = true, chosenCategory = category, chosenLabel = label))
    }

    /** PENDING → SENDING. false, якщо запис уже надсилається або готовий (захист від подвійного надсилання). */
    suspend fun startSending(id: Long): Boolean =
        dao.transition(id, HistoryStatus.PENDING.name, HistoryStatus.SENDING.name) == 1

    /** SENDING → DONE з результатом; час запису оновлюється на час отримання результату. */
    suspend fun completeSending(id: Long, result: Categorization) {
        val e = dao.get(id) ?: return
        dao.update(result.toEntity(e.inputText, clock()).copy(id = e.id))
    }

    /** SENDING → PENDING після помилки: запит лишається для наступної спроби. */
    suspend fun failSending(id: Long) {
        dao.transition(id, HistoryStatus.SENDING.name, HistoryStatus.PENDING.name)
    }

    suspend fun resetStuckSending() = dao.resetStuckSending()

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}
