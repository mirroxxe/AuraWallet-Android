package com.aurawallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurawallet.app.data.AppError
import com.aurawallet.app.data.BudgetSummary
import com.aurawallet.app.data.Categorization
import com.aurawallet.app.data.SavedTransaction
import com.aurawallet.app.data.TransactionRepository
import com.aurawallet.app.data.toAppError
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
    data class Success(val result: Categorization, val chosenCategory: String, val chosenLabel: String) : ResultState
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
) {
    val canSubmit: Boolean get() = input.isNotBlank() && result !is ResultState.Loading
}

class CategorizeViewModel(
    private val repository: TransactionRepository = TransactionRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(CategorizeUiState())
    val state: StateFlow<CategorizeUiState> = _state.asStateFlow()

    private var request: Job? = null
    private var budgetRequest: Job? = null
    private var nextId = 1L

    init { checkServer() }

    fun checkServer() {
        _state.update { it.copy(server = ServerStatus.Checking) }
        viewModelScope.launch {
            val up = repository.isServerUp()
            _state.update { it.copy(server = if (up) ServerStatus.Online else ServerStatus.Offline) }
        }
    }

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
        val validation = validate(text)
        if (validation != null) {
            _state.update { it.copy(inputError = validation) }
            return
        }
        request?.cancel()
        _state.update { it.copy(result = ResultState.Loading, inputError = null) }
        request = viewModelScope.launch {
            repository.categorize(text)
                .onSuccess { r ->
                    _state.update { it.copy(result = ResultState.Success(r, r.category, r.label), server = ServerStatus.Online) }
                }
                .onFailure { e ->
                    val error = e.toAppError()
                    _state.update {
                        it.copy(
                            result = ResultState.Error(error),
                            server = if (error is AppError.Network || error is AppError.Timeout) ServerStatus.Offline else it.server,
                        )
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

    fun save() {
        val success = _state.value.result as? ResultState.Success ?: return
        val r = success.result
        val tx = SavedTransaction(
            id = nextId++, text = _state.value.input.trim(), category = success.chosenCategory,
            label = success.chosenLabel, amount = r.amount, currency = r.currency, isIncome = r.isIncome,
        )
        _state.update { it.copy(saved = listOf(tx) + it.saved, input = "", result = ResultState.Idle) }
        refreshBudget()
    }

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
            repository.budgetSummary(snapshot.monthlyBudget, snapshot.saved)
                .onSuccess { b -> _state.update { it.copy(budget = BudgetState.Ready(b)) } }
                .onFailure { e -> _state.update { it.copy(budget = BudgetState.Error(e.toAppError())) } }
        }
    }

    fun reset() {
        request?.cancel()
        _state.update { it.copy(result = ResultState.Idle) }
    }

    companion object {
        const val MAX_LENGTH = 200
        const val DEFAULT_BUDGET = 18_000.0  // як у iOS-версії

        fun parseBudget(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 10_000_000 }

        fun validate(text: String): String? = when {
            text.isBlank() -> "Введіть опис операції"
            text.length < 2 -> "Опис занадто короткий"
            text.none { it.isLetter() } -> "Додайте хоча б одне слово, напр. «кава 85»"
            else -> null
        }
    }
}
