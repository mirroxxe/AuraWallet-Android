package com.aurawallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aurawallet.app.AuraApplication
import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.BudgetSummary
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.SavedTransaction
import com.aurawallet.app.domain.model.toAppError
import com.aurawallet.app.domain.repository.BudgetRepository
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.DataSourceSwitch
import com.aurawallet.app.domain.repository.HistoryStore
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase
import com.aurawallet.app.domain.usecase.InputValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Стан екрана: початковий, завантаження, успіх або помилка. */
sealed interface ResultState {
    data object Idle : ResultState
    data object Loading : ResultState
    /** [recordId] — id запису в локальній історії (ЛР4), створеного одразу після успішної відповіді. */
    data class Success(
        val result: Categorization,
        val chosenCategory: String,
        val chosenLabel: String,
        val recordId: Long? = null,
    ) : ResultState
    data class Error(val error: AppError) : ResultState
}

enum class ServerStatus { Checking, Online, Offline }

/** Стан картки «Бюджет місяця» (ЛР3). Незалежний від стану розпізнавання. */
sealed interface BudgetState {
    data object Empty : BudgetState
    data object Loading : BudgetState
    data class Ready(val summary: BudgetSummary) : BudgetState
    data class Error(val error: AppError) : BudgetState
}

data class CategorizeUiState(
    val input: String = "",
    val inputError: String? = null,
    val result: ResultState = ResultState.Idle,
    val saved: List<SavedTransaction> = emptyList(),
    val server: ServerStatus = ServerStatus.Checking,
    val monthlyBudget: Double = CategorizeViewModel.DEFAULT_BUDGET,
    val budgetInput: String = CategorizeViewModel.DEFAULT_BUDGET.toInt().toString(),
    val budgetInputError: String? = null,
    val budget: BudgetState = BudgetState.Empty,
    /** Коротке повідомлення внизу екрана, напр. «Запит збережено в черзі». */
    val notice: String? = null,
    /** ЛР5: увімкнено демо-джерело (fake repository) замість backend. */
    val demoMode: Boolean = false,
) {
    val canSubmit: Boolean get() = input.isNotBlank() && result !is ResultState.Loading
}

/**
 * Presentation-логіка головного екрана. Залежності приходять через конструктор (DI):
 * лише use case та інтерфейси domain — без Retrofit, Room і BuildConfig.
 */
class CategorizeViewModel(
    private val categorize: CategorizeTransactionUseCase,
    private val categorization: CategorizationRepository,
    private val budgets: BudgetRepository,
    private val history: HistoryStore,
    private val dataSource: DataSourceSwitch = DataSourceSwitch.Fixed,
) : ViewModel() {

    private val _state = MutableStateFlow(CategorizeUiState())
    val state: StateFlow<CategorizeUiState> = _state.asStateFlow()

    private var request: Job? = null
    private var budgetRequest: Job? = null

    init {
        // ЛР5: при перемиканні джерела оновлюємо прапорець і заново перевіряємо доступність.
        viewModelScope.launch {
            dataSource.isDemo.collect { demo ->
                _state.update { it.copy(demoMode = demo) }
                checkServer()
            }
        }
        // ЛР4: підтверджені операції читаються з Room, тож список і бюджет переживають перезапуск.
        viewModelScope.launch {
            history.observeConfirmed().collect { saved ->
                _state.update { it.copy(saved = saved) }
                refreshBudget()
            }
        }
    }

    fun checkServer() {
        _state.update { it.copy(server = ServerStatus.Checking) }
        viewModelScope.launch {
            val up = categorization.isAvailable()
            _state.update { it.copy(server = if (up) ServerStatus.Online else ServerStatus.Offline) }
        }
    }

    fun setDemoMode(enabled: Boolean) = dataSource.setDemo(enabled)

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value.take(MAX_LENGTH), inputError = null) }
    }

    fun useExample(example: String) {
        onInputChange(example)
        submit()
    }

    /** Відправляє запит. Порожні дані не надсилаються; повторний виклик скасовує попередній запит. */
    fun submit() {
        val text = _state.value.input.trim()
        // Швидка перевірка до показу Loading; той самий валідатор повторно спрацює в use case.
        InputValidator.validate(text)?.let { invalid ->
            _state.update { it.copy(inputError = invalid.userMessage) }
            return
        }
        request?.cancel()
        _state.update { it.copy(result = ResultState.Loading, inputError = null) }
        request = viewModelScope.launch {
            categorize(text)
                .onSuccess { entry ->
                    val r = entry.result
                    _state.update { it.copy(result = ResultState.Success(r, r.category, r.label, entry.recordId), server = ServerStatus.Online) }
                }
                .onFailure { e ->
                    when (val error = e.toAppError()) {
                        is AppError.Validation -> _state.update { it.copy(result = ResultState.Idle, inputError = error.userMessage) }
                        else -> _state.update {
                            it.copy(result = ResultState.Error(error), server = if (error.isConnectivity) ServerStatus.Offline else it.server)
                        }
                    }
                }
        }
    }

    fun retry() = submit()

    fun chooseCategory(category: String, label: String) {
        _state.update { s ->
            val current = s.result as? ResultState.Success ?: return@update s
            s.copy(result = current.copy(chosenCategory = category, chosenLabel = label))
        }
    }

    /** «Зберегти»: позначає запис історії як підтверджену операцію. Список і бюджет оновляться з Room. */
    fun save() {
        val success = _state.value.result as? ResultState.Success ?: return
        val id = success.recordId
        _state.update { it.copy(input = "", result = ResultState.Idle) }
        if (id != null) viewModelScope.launch { history.confirm(id, success.chosenCategory, success.chosenLabel) }
    }

    /** ЛР4, підвищений рівень: без мережі зберегти запит як «очікує», щоб надіслати пізніше з історії. */
    fun queueOffline() {
        val text = _state.value.input.trim()
        if (InputValidator.validate(text) != null) return
        val err = (_state.value.result as? ResultState.Error)?.error
        if (err == null || !err.isConnectivity) return
        _state.update { it.copy(input = "", result = ResultState.Idle, notice = "Запит збережено в історії зі статусом «очікує»") }
        viewModelScope.launch { history.addPending(text) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ---------- Бюджет місяця (ЛР3) ----------

    fun onBudgetInputChange(value: String) {
        _state.update { it.copy(budgetInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9), budgetInputError = null) }
    }

    fun applyBudget() {
        val parsed = parseBudget(_state.value.budgetInput)
        if (parsed == null) {
            _state.update { it.copy(budgetInputError = "Введіть суму більше нуля") }
            return
        }
        _state.update { it.copy(monthlyBudget = parsed, budgetInputError = null) }
        refreshBudget()
    }

    /** Перераховує бюджет на backend. Порожній список — показуємо підказку без запиту. */
    fun refreshBudget() {
        val snapshot = _state.value
        if (snapshot.saved.isEmpty()) {
            _state.update { it.copy(budget = BudgetState.Empty) }
            return
        }
        budgetRequest?.cancel()
        _state.update { it.copy(budget = BudgetState.Loading) }
        budgetRequest = viewModelScope.launch {
            budgets.summary(snapshot.monthlyBudget, snapshot.saved)
                .onSuccess { b -> _state.update { it.copy(budget = BudgetState.Ready(b)) } }
                .onFailure { e -> _state.update { it.copy(budget = BudgetState.Error(e.toAppError())) } }
        }
    }

    fun reset() {
        request?.cancel()
        _state.update { it.copy(result = ResultState.Idle) }
    }

    companion object {
        /** Фабрика: залежності беруться з [AuraApplication] (без DI-бібліотеки). */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApplication
                CategorizeViewModel(app.categorizeUseCase, app.categorization, app.budgets, app.history, app.dataSource)
            }
        }

        const val MAX_LENGTH = InputValidator.MAX_LENGTH
        const val DEFAULT_BUDGET = 18_000.0  // як у iOS-версії

        fun parseBudget(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 10_000_000 }
    }
}
