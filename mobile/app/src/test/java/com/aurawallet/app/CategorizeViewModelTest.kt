package com.aurawallet.app

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.usecase.CategorizeTransactionUseCase
import com.aurawallet.app.fakes.FakeBudgetRepository
import com.aurawallet.app.fakes.InMemoryHistoryStore
import com.aurawallet.app.fakes.ScriptedCategorizationRepository
import com.aurawallet.app.fakes.categorization
import com.aurawallet.app.fakes.failure
import com.aurawallet.app.ui.BudgetState
import com.aurawallet.app.ui.CategorizeViewModel
import com.aurawallet.app.ui.ResultState
import com.aurawallet.app.ui.ServerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ЛР5: ViewModel перевіряється з fake-залежностями — без backend, Room і емулятора. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategorizeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: ScriptedCategorizationRepository,
        history: InMemoryHistoryStore = InMemoryHistoryStore(),
        budgets: FakeBudgetRepository = FakeBudgetRepository(),
    ) = CategorizeViewModel(CategorizeTransactionUseCase(repo, history), repo, budgets, history)

    @Test
    fun successShowsResultLinkedToHistoryRecord() = runTest {
        val vm = viewModel(ScriptedCategorizationRepository(Result.success(categorization())))
        vm.onInputChange("Кава в Aroma Kava 85 грн")
        vm.submit()
        advanceUntilIdle()

        val result = vm.state.value.result as ResultState.Success
        assertEquals("coffee", result.result.category)
        assertEquals(1L, result.recordId)
        assertEquals(ServerStatus.Online, vm.state.value.server)
    }

    @Test
    fun networkErrorThenRetryKeepsInputAndSucceeds() = runTest {
        val repo = ScriptedCategorizationRepository(listOf(failure(AppError.Network), Result.success(categorization())))
        val vm = viewModel(repo)
        vm.onInputChange("таксі 180")
        vm.submit()
        advanceUntilIdle()

        assertEquals(ResultState.Error(AppError.Network), vm.state.value.result)
        assertEquals(ServerStatus.Offline, vm.state.value.server)
        assertEquals("таксі 180", vm.state.value.input)

        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value.result is ResultState.Success)
        assertEquals(2, repo.calls.size)
    }

    @Test
    fun invalidInputShowsFieldErrorWithoutRequest() = runTest {
        val repo = ScriptedCategorizationRepository()
        val vm = viewModel(repo)
        vm.onInputChange("12345")
        vm.submit()
        advanceUntilIdle()

        assertEquals("Додайте хоча б одне слово, напр. «кава 85»", vm.state.value.inputError)
        assertEquals(ResultState.Idle, vm.state.value.result)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun saveConfirmsRecordAndRecalculatesBudget() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = viewModel(ScriptedCategorizationRepository(Result.success(categorization())), budgets = budgets)
        vm.onInputChange("Кава 85")
        vm.submit()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.saved.size)
        assertTrue(vm.state.value.budget is BudgetState.Ready)
        assertTrue(budgets.calls >= 1)
        assertNull(vm.state.value.inputError)
    }

    @Test
    fun offlineRequestCanBeQueued() = runTest {
        val history = InMemoryHistoryStore()
        val vm = viewModel(ScriptedCategorizationRepository(failure(AppError.Timeout)), history)
        vm.onInputChange("таксі 180")
        vm.submit()
        advanceUntilIdle()
        vm.queueOffline()
        advanceUntilIdle()

        assertEquals(listOf("таксі 180"), history.pending)
        assertEquals(ResultState.Idle, vm.state.value.result)
    }
}
