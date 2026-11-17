package com.nexora.trade.ui.screens.trade

import com.nexora.trade.data.repository.DaySnapshot
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.AlertEvaluator
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.calculator.ThirtyDayAggregator
import com.nexora.trade.domain.model.AlertEvaluationResult
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.screens.dashboard.DashboardViewModelTest.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditTradeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDate = LocalDate.of(2026, 5, 22)

    @Test
    fun `add trade validates input requires confirmation and triggers target alert once`() = runTest {
        val repo = InMemoryRepo().apply {
            setStartingBalance(testDate, BigDecimal("100.00"))
        }

        val viewModel = AddEditTradeViewModel(
            repository = repo,
            date = testDate,
            tradeId = -1L
        )

        viewModel.updateAmountInput("0")
        viewModel.requestSaveConfirmation()
        assertNotNull(viewModel.uiState.value.amountError)
        assertNull(viewModel.uiState.value.pendingConfirmAmount)

        viewModel.selectTradeType(TradeType.PROFIT)
        viewModel.updateAmountInput("10")
        viewModel.updateNoteInput("Clean breakout")
        viewModel.requestSaveConfirmation()

        assertEquals(0, BigDecimal("10.00").compareTo(viewModel.uiState.value.pendingConfirmAmount))
        viewModel.confirmSaveTrade()

        assertEquals(DailyAlertType.PROFIT_TARGET_COMPLETED, viewModel.uiState.value.triggeredAlert)
        val snap = repo.observeDaySnapshot(testDate).first()
        assertEquals(1, snap.trades.size)
        assertEquals("Clean breakout", snap.trades.first().note)

        viewModel.dismissAlertAndFinish()
        assertTrue(viewModel.uiState.value.shouldNavigateBack)
    }

    @Test
    fun `edit trade loads existing entry and updates daily totals`() = runTest {
        val repo = InMemoryRepo().apply {
            setStartingBalance(testDate, BigDecimal("100.00"))
            addTrade(testDate, TradeType.PROFIT, BigDecimal("6.00"), "Old note", 1000L)
        }
        val savedId = repo.observeDaySnapshot(testDate).first().trades.first().id

        val editViewModel = AddEditTradeViewModel(
            repository = repo,
            date = testDate,
            tradeId = savedId
        )

        assertTrue(editViewModel.uiState.value.isEditMode)
        assertEquals("6", editViewModel.uiState.value.amountInput)
        assertEquals("Old note", editViewModel.uiState.value.noteInput)

        editViewModel.selectTradeType(TradeType.LOSS)
        editViewModel.updateAmountInput("5")
        editViewModel.updateNoteInput("Adjusted loss")
        editViewModel.requestSaveConfirmation()
        editViewModel.confirmSaveTrade()

        assertEquals(DailyAlertType.LOSS_LIMIT_REACHED, editViewModel.uiState.value.triggeredAlert)
        val updatedSnap = repo.observeDaySnapshot(testDate).first()
        assertEquals(0, BigDecimal("-5.00").compareTo(updatedSnap.summary.netProfitLoss))
        assertEquals("Adjusted loss", updatedSnap.trades.first().note)
    }

    private class InMemoryRepo : TradingRepository {
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
            val normalized = MoneyCalculator.normalizeCurrency(startingBalance)
            records.value = records.value + (date to DailyRecord(date = date, startingBalance = normalized))
            return AlertEvaluationResult(null, false, false)
        }

        override suspend fun addTrade(
            date: LocalDate,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            timestampMillis: Long
        ): AlertEvaluationResult {
            val id = nextId++
            trades.value = trades.value + TradeEntry(id, date, type, amount, note, timestampMillis)
            return evaluateForDate(date)
        }

        override suspend fun updateTrade(
            tradeId: Long,
            type: TradeType,
            amount: BigDecimal,
            note: String
        ): AlertEvaluationResult {
            val existing = trades.value.first { it.id == tradeId }
            trades.value = trades.value.map {
                if (it.id == tradeId) it.copy(type = type, amount = amount, note = note) else it
            }
            return evaluateForDate(existing.date)
        }

        override suspend fun deleteTrade(tradeId: Long) {
            trades.value = trades.value.filterNot { it.id == tradeId }
        }

        override suspend fun saveDailyReflection(date: LocalDate, reflectionText: String) = Unit

        private fun evaluateForDate(date: LocalDate): AlertEvaluationResult {
            val rec = records.value[date]
            val dayTrades = trades.value.filter { it.date == date }
            val summary = MoneyCalculator.calculateDailySummary(date, rec?.startingBalance, dayTrades)
            val eval = AlertEvaluator.evaluate(summary, rec)
            if (rec != null) {
                records.value = records.value + (
                    date to rec.copy(
                        targetAlertTriggered = eval.updatedTargetAlertTriggered,
                        lossAlertTriggered = eval.updatedLossAlertTriggered
                    )
                )
            }
            return eval
        }
    }
}