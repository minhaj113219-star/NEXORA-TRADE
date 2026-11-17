package com.nexora.trade.ui.screens.thirtyday

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
import com.nexora.trade.ui.screens.history.TradeHistoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ThirtyDayAndHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val endDate = LocalDate.of(2026, 5, 30)

    @Test
    fun `thirty day view model filters recorded and target days accurately`() = runTest {
        val repo = FakeRepo().apply {
            setStartingBalance(endDate, BigDecimal("100.00"))
            addTrade(endDate, TradeType.PROFIT, BigDecimal("10.00"), "Target", 2000L)

            val prevDay = endDate.minusDays(1)
            setStartingBalance(prevDay, BigDecimal("200.00"))
            addTrade(prevDay, TradeType.LOSS, BigDecimal("10.00"), "Stop", 1000L)
        }

        val viewModel = ThirtyDayViewModel(repo) { endDate }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(30, viewModel.uiState.value.filteredDays.size)
        assertEquals(2, viewModel.uiState.value.summary.recordedDaysCount)

        viewModel.selectFilter(ThirtyDayFilter.RECORDED_ONLY)
        assertEquals(2, viewModel.uiState.value.filteredDays.size)

        viewModel.selectFilter(ThirtyDayFilter.TARGETS_HIT)
        assertEquals(1, viewModel.uiState.value.filteredDays.size)
        assertEquals(endDate, viewModel.uiState.value.filteredDays.first().date)

        viewModel.selectFilter(ThirtyDayFilter.LOSS_LIMITS)
        assertEquals(1, viewModel.uiState.value.filteredDays.size)
        assertEquals(endDate.minusDays(1), viewModel.uiState.value.filteredDays.first().date)

        job.cancel()
    }

    @Test
    fun `trade history view model groups trades descending by date and calculates cumulative pnl`() = runTest {
        val day1 = endDate.minusDays(2)
        val day2 = endDate

        val repo = FakeRepo().apply {
            addTrade(day1, TradeType.PROFIT, BigDecimal("15.00"), "Trade A", 1000L)
            addTrade(day2, TradeType.LOSS, BigDecimal("5.00"), "Trade B", 2000L)
            addTrade(day2, TradeType.PROFIT, BigDecimal("8.00"), "Trade C", 3000L)
        }

        val viewModel = TradeHistoryViewModel(repo)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        assertEquals(3, state.totalTradesCount)
        assertEquals(0, BigDecimal("18.00").compareTo(state.totalNetProfitLoss))
        assertEquals(2, state.dayGroups.size)

        assertEquals(day2, state.dayGroups[0].date)
        assertEquals(2, state.dayGroups[0].trades.size)
        assertEquals(0, BigDecimal("3.00").compareTo(state.dayGroups[0].dayNetProfitLoss))

        assertEquals(day1, state.dayGroups[1].date)
        assertEquals(1, state.dayGroups[1].trades.size)
        assertEquals(0, BigDecimal("15.00").compareTo(state.dayGroups[1].dayNetProfitLoss))

        job.cancel()
    }

    private class FakeRepo : TradingRepository {
        private val records = MutableStateFlow<Map<LocalDate, DailyRecord>>(emptyMap())
        private val trades = MutableStateFlow<List<TradeEntry>>(emptyList())
        private var nextId = 1L

        override fun observeDaySnapshot(date: LocalDate): Flow<DaySnapshot> =
            combine(records, trades) { recMap, tradeList ->
                val rec = recMap[date]
                val dayTrades = tradeList.filter { it.date == date }
                DaySnapshot(
                    date = date,
                    record = rec,
                    trades = dayTrades,
                    summary = MoneyCalculator.calculateDailySummary(date, rec?.startingBalance, dayTrades)
                )
            }

        override fun observeThirtyDaySummary(endDate: LocalDate): Flow<ThirtyDaySummary> =
            combine(records, trades) { recMap, tradeList ->
                ThirtyDayAggregator.buildSummary(endDate, recMap, tradeList.groupBy { it.date })
            }

        override fun observeAllTrades(): Flow<List<TradeEntry>> = trades

        override fun observeDailyRecord(date: LocalDate): Flow<DailyRecord?> =
            records.map { it[date] }

        override suspend fun getTradeById(tradeId: Long): TradeEntry? =
            trades.value.firstOrNull { it.id == tradeId }

        override suspend fun setStartingBalance(
            date: LocalDate,
            startingBalance: BigDecimal
        ): AlertEvaluationResult {
            records.value = records.value + (
                date to DailyRecord(date = date, startingBalance = startingBalance)
            )
            return AlertEvaluationResult(null, false, false)
        }

        override suspend fun addTrade(
            date: LocalDate,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            timestampMillis: Long
        ): AlertEvaluationResult {
            trades.value = trades.value + TradeEntry(nextId++, date, type, amount, note, timestampMillis)
            return AlertEvaluationResult(null, false, false)
        }

        override suspend fun updateTrade(
            tradeId: Long,
            type: TradeType,
            amount: BigDecimal,
            note: String
        ): AlertEvaluationResult = AlertEvaluationResult(null, false, false)

        override suspend fun deleteTrade(tradeId: Long) = Unit

        override suspend fun saveDailyReflection(date: LocalDate, reflectionText: String) = Unit
    }
}