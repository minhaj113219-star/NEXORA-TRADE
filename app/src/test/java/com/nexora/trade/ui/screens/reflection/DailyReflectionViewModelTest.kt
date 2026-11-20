package com.nexora.trade.ui.screens.reflection

import com.nexora.trade.data.repository.DaySnapshot
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.calculator.ThirtyDayAggregator
import com.nexora.trade.domain.model.AlertEvaluationResult
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.screens.dashboard.DashboardViewModelTest.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReflectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dayOne = LocalDate.of(2026, 5, 25)
    private val dayTwo = LocalDate.of(2026, 5, 26)

    @Test
    fun `reflection loads per date tracks unsaved changes and saves without altering other days`() = runTest {
        val repo = InMemoryRepo().apply {
            saveDailyReflection(dayOne, "Day 1 note")
            saveDailyReflection(dayTwo, "Day 2 note")
        }

        val viewModel = DailyReflectionViewModel(repo, dayOne)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Day 1 note", viewModel.uiState.value.reflectionInput)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.updateReflectionInput("Updated day 1 discipline note")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.saveReflection()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(viewModel.uiState.value.shouldNavigateBack)

        assertEquals(
            "Updated day 1 discipline note",
            repo.observeDailyRecord(dayOne).first()?.reflectionText
        )
        assertEquals(
            "Day 2 note",
            repo.observeDailyRecord(dayTwo).first()?.reflectionText
        )
    }

    @Test
    fun `back request prompts discard dialog only when unsaved changes exist`() = runTest {
        val repo = InMemoryRepo()
        val viewModel = DailyReflectionViewModel(repo, dayOne)

        viewModel.updateReflectionInput("Draft text")
        viewModel.onBackRequested()
        assertTrue(viewModel.uiState.value.showDiscardDialog)
        assertFalse(viewModel.uiState.value.shouldNavigateBack)

        viewModel.confirmDiscardChanges()
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertTrue(viewModel.uiState.value.shouldNavigateBack)
    }

    private class InMemoryRepo : TradingRepository {
        private val records = MutableStateFlow<Map<LocalDate, DailyRecord>>(emptyMap())

        override fun observeDaySnapshot(date: LocalDate): Flow<DaySnapshot> =
            records.map { map ->
                val rec = map[date]
                DaySnapshot(
                    date = date,
                    record = rec,
                    trades = emptyList(),
                    summary = MoneyCalculator.calculateDailySummary(date, rec?.startingBalance, emptyList())
                )
            }

        override fun observeThirtyDaySummary(endDate: LocalDate): Flow<ThirtyDaySummary> =
            records.map { ThirtyDayAggregator.buildSummary(endDate, it, emptyMap()) }

        override fun observeAllTrades(): Flow<List<TradeEntry>> = MutableStateFlow(emptyList())

        override fun observeDailyRecord(date: LocalDate): Flow<DailyRecord?> =
            records.map { it[date] }

        override suspend fun getTradeById(tradeId: Long): TradeEntry? = null

        override suspend fun ensureCarryOverStartingBalance(date: LocalDate): AlertEvaluationResult? = null

        override suspend fun setStartingBalance(
            date: LocalDate,
            startingBalance: BigDecimal
        ): AlertEvaluationResult = AlertEvaluationResult(null, false, false)

        override suspend fun addTrade(
            date: LocalDate,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            timestampMillis: Long,
            stakeAmount: BigDecimal?,
            payoutPercent: BigDecimal?
        ): AlertEvaluationResult = AlertEvaluationResult(null, false, false)

        override suspend fun updateTrade(
            tradeId: Long,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            stakeAmount: BigDecimal?,
            payoutPercent: BigDecimal?
        ): AlertEvaluationResult = AlertEvaluationResult(null, false, false)

        override suspend fun deleteTrade(tradeId: Long) = Unit

        override suspend fun saveDailyReflection(date: LocalDate, reflectionText: String) {
            val existing = records.value[date]
            records.value = records.value + (
                date to (existing?.copy(reflectionText = reflectionText)
                    ?: DailyRecord(date = date, startingBalance = null, reflectionText = reflectionText))
            )
        }
    }
}
