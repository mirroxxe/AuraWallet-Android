package com.aurawallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aurawallet.app.AuraApplication
import com.aurawallet.app.data.repository.HistoryRecord
import com.aurawallet.app.data.repository.HistoryRepository
import com.aurawallet.app.data.repository.HistoryStatus
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.toAppError
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.usecase.InputValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ЛР4: історія запитів, деталі, видалення, ручне надсилання «очікує». */
class HistoryViewModel(
    private val history: HistoryRepository,
    private val categorization: CategorizationRepository,
) : ViewModel() {

    /** null — дані ще завантажуються з Room. */
    val records: StateFlow<List<HistoryRecord>?> =
        history.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Записи, які надсилаються з цього екрана зараз (додатковий захист від подвійного натискання). */
    private val inFlight = mutableSetOf<Long>()

    init {
        // Якщо застосунок закрили під час надсилання, запис не має назавжди лишитися «надсилається».
        viewModelScope.launch { history.resetStuckSending() }
    }

    fun record(id: Long): Flow<HistoryRecord?> = history.observe(id)

    fun send(record: HistoryRecord) {
        if (record.status != HistoryStatus.PENDING) return
        InputValidator.validate(record.inputText)?.let { invalid -> _message.update { invalid.userMessage }; return }
        if (!inFlight.add(record.id)) return
        viewModelScope.launch {
            try {
                if (!history.startSending(record.id)) return@launch   // уже надсилається або готовий
                categorization.categorize(CategorizationInput(record.inputText))
                    .onSuccess { r ->
                        history.completeSending(record.id, r)
                        _message.update { "Готово: «${record.inputText}» → ${r.label}" }
                    }
                    .onFailure { e ->
                        history.failSending(record.id)
                        _message.update { "Не надіслано: ${e.toAppError().userMessage} Запит лишився в черзі." }
                    }
            } finally {
                inFlight.remove(record.id)
            }
        }
    }

    fun delete(id: Long) = viewModelScope.launch { history.delete(id) }

    fun clear() = viewModelScope.launch { history.clear() }

    fun dismissMessage() = _message.update { null }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApplication
                HistoryViewModel(app.history, app.categorization)
            }
        }
    }
}
