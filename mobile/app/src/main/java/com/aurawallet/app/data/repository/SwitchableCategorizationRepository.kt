package com.aurawallet.app.data.repository

import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.repository.CategorizationRepository
import com.aurawallet.app.domain.repository.DataSourceSwitch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug-перемикач між двома взаємозамінними реалізаціями [CategorizationRepository].
 * Сам теж є [CategorizationRepository], тож use case і ViewModel не змінюються.
 */
class SwitchableCategorizationRepository(
    private val real: CategorizationRepository,
    private val demo: CategorizationRepository,
    startInDemo: Boolean = false,
) : CategorizationRepository, DataSourceSwitch {

    private val _isDemo = MutableStateFlow(startInDemo)
    override val isDemo: StateFlow<Boolean> = _isDemo.asStateFlow()

    override fun setDemo(enabled: Boolean) {
        _isDemo.value = enabled
    }

    private val current: CategorizationRepository get() = if (_isDemo.value) demo else real

    override suspend fun categorize(input: CategorizationInput): Result<Categorization> = current.categorize(input)

    override suspend fun isAvailable(): Boolean = current.isAvailable()
}
