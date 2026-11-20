package com.nexora.trade.ui.screens.dashboard

import com.nexora.trade.data.repository.DaySnapshot
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.AlertEvaluator
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.calculator.ThirtyDayAggregator
import com.nexora.trade.domain.model.AlertEvaluationResult
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDate = LocalDate.of(2026, 5, 20)

    @Test
    fun `setting and editing starting balance validates input and requires confirmation on edit`() = runTest {
        val fakeRepo = FakeTradingRepository()
        val viewModel = DashboardViewModel(
            repository = fakeRepo,
            dateProvider = { testDate }
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertFalse(viewModel.uiState.value.summary.hasValidStartingBalance)
        assertEquals(DailyStatus.NO_STARTING_BALANCE, viewModel.uiState.value.summary.status)

        // Invalid input shows error
        viewModel.openStartingBalanceDialog()
        viewModel.updateStartingBalanceInput("-50")
        viewModel.submitStartingBalanceInput()
        val errorState = viewModel.uiState.value.dialogState as StartingBalanceDialogState.Input
        assertNotNull(errorState.errorMessage)

        // Initial valid starting balance saves directly
        viewModel.updateStartingBalanceInput("100")
        viewModel.submitStartingBalanceInput()
        assertEquals(StartingBalanceDialogState.Hidden, viewModel.uiState.value.dialogState)
        assertTrue(viewModel.uiState.value.summary.hasValidStartingBalance)
        assertEquals(0, BigDecimal("100.00").compareTo(viewModel.uiState.value.summary.startingBalance))
        assertEquals(0, BigDecimal("10.00").compareTo(viewModel.uiState.value.summary.dailyProfitTarget))
        assertEquals(0, BigDecimal("5.00").compareTo(viewModel.uiState.value.summary.maxDailyLoss))

        // Editing existing starting balance transitions to ConfirmEdit
        viewModel.openStartingBalanceDialog()
        viewModel.updateStartingBalanceInput("250")
        viewModel.submitStartingBalanceInput()
        assertTrue(viewModel.uiState.value.dialogState is StartingBalanceDialogState.ConfirmEdit)

        // Balance not changed until confirmed
        assertEquals(0, BigDecimal("100.00").compareTo(viewModel.uiState.value.summary.startingBalance))

        viewModel.confirmEditStartingBalance()
        assertEquals(StartingBalanceDialogState.Hidden, viewModel.uiState.value.dialogState)
        assertEquals(0, BigDecimal("250.00").compareTo(viewModel.uiState.value.summary.startingBalance))
        assertEquals(0, BigDecimal("25.00").compareTo(viewModel.uiState.value.summary.dailyProfitTarget))
        assertEquals(0, BigDecimal("12.50").compareTo(viewModel.uiState.value.summary.maxDailyLoss))

        collectJob.cancel()
    }

    @Test
    fun `dashboard auto carries over previous day ending balance when today has no starting balance`() = runTest {
        val fakeRepo = FakeTradingRepository().apply {
            val yesterday = testDate.minusDays(1)
            setStartingBalance(yesterday, BigDecimal("100.00"))
            addTrade(yesterday, TradeType.LOSS, BigDecimal("10.00"), "Loss", 1000L)
        }

        val viewModel = DashboardViewModel(
            repository = fakeRepo,
            dateProvider = { testDate }
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue(viewModel.uiState.value.summary.hasValidStartingBalance)
        assertEquals(0, BigDecimal("90.00").compareTo(viewModel.uiState.value.summary.startingBalance))
        assertEquals(0, BigDecimal("90.00").compareTo(viewModel.uiState.value.summary.currentBalance))

        collectJob.cancel()
    }

    private class FakeTradingRepository : TradingRepository {
        private val records = MutableStateFlow<Map<LocalDate, DailyRecord>>(emptyMap())
        private val trades = MutableStateFlow<List<TradeEntry>>(emptyList())

        override fun observeDaySnapshot(date: LocalDate): Flow<DaySnapshot> {
            return combine(records, trades) { recMap, tradeList ->
                val record = recMap[date]
                val dayTrades = tradeList.filter { it.date == date }
                DaySnapshot(
                    date = date,
                    record = record,
                    trades = dayTrades,
                    summary = MoneyCalculator.calculateDailySummary(
                        date = date,
                        startingBalance = record?.startingBalance,
                        trades = dayTrades
                    )
                )
            }
        }

        override fun observeThirtyDaySummary(endDate: LocalDate): Flow<ThirtyDaySummary> {
            return combine(records, trades) { recMap, tradeList ->
                ThirtyDayAggregator.buildSummary(
                    endDate = endDate,
                    recordsByDate = recMap,
                    tradesByDate = tradeList.groupBy { it.date }
                )
            }
        }

        override fun observeAllTrades(): Flow<List<TradeEntry>> = trades

        override fun observeDailyRecord(date: LocalDate): Flow<DailyRecord?> =
            records.map { it[date] }

        override suspend fun getTradeById(tradeId: Long): TradeEntry? =
            trades.value.firstOrNull { it.id == tradeId }

        override suspend fun ensureCarryOverStartingBalance(date: LocalDate): AlertEvaluationResult? {
            val existingToday = records.value[date]
            if (existingToday?.startingBalance != null && !existingToday.isAutoCarriedOver) {
                return null
            }
            val prevRecord = records.value.values
                .filter {
                    it.date < date &&
                        it.startingBalance != null &&
                        it.startingBalance > BigDecimal.ZERO
                }
                .maxByOrNull { it.date }
                ?: return null

            val prevTrades = trades.value.filter { it.date == prevRecord.date }
            val prevSummary = MoneyCalculator.calculateDailySummary(
                date = prevRecord.date,
                startingBalance = prevRecord.startingBalance,
                trades = prevTrades
            )
            if (prevSummary.currentBalance <= BigDecimal.ZERO) {
                return null
            }

            val normalized = MoneyCalculator.normalizeCurrency(prevSummary.currentBalance)
            val todayTrades = trades.value.filter { it.date == date }
            val todaySummary = MoneyCalculator.calculateDailySummary(date, normalized, todayTrades)
            val baseline = (existingToday ?: DailyRecord(date = date, startingBalance = normalized))
                .copy(startingBalance = normalized, isAutoCarriedOver = true)
            val eval = AlertEvaluator.evaluate(todaySummary, baseline)

            records.value = records.value + (
                date to baseline.copy(
                    targetAlertTriggered = eval.updatedTargetAlertTriggered,
                    lossAlertTriggered = eval.updatedLossAlertTriggered
                )
            )
            return eval
        }

        override suspend fun setStartingBalance(
            date: LocalDate,
            startingBalance: BigDecimal
        ): AlertEvaluationResult {
            val normalized = MoneyCalculator.normalizeCurrency(startingBalance)
            val existing = records.value[date]
            val dayTrades = trades.value.filter { it.date == date }
            val summary = MoneyCalculator.calculateDailySummary(date, normalized, dayTrades)
            val eval = AlertEvaluator.evaluate(summary, existing?.copy(startingBalance = normalized))
            records.value = records.value + (
                date to DailyRecord(
                    date = date,
                    startingBalance = normalized,
                    targetAlertTriggered = eval.updatedTargetAlertTriggered,
                    lossAlertTriggered = eval.updatedLossAlertTriggered,
                    reflectionText = existing?.reflectionText.orEmpty(),
                    isAutoCarriedOver = false
                )
            )
            return eval
        }

        override suspend fun addTrade(
            date: LocalDate,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            timestampMillis: Long,
            stakeAmount: BigDecimal?,
            payoutPercent: BigDecimal?
        ): AlertEvaluationResult {
            val id = (trades.value.maxOfOrNull { it.id } ?: 0L) + 1L
            trades.value = trades.value + TradeEntry(
                id = id,
                date = date,
                type = type,
                amount = amount,
                note = note,
                timestampMillis = timestampMillis,
                stakeAmount = stakeAmount,
                payoutPercent = payoutPercent
            )
            return AlertEvaluationResult(null, false, false)
        }

        override suspend fun updateTrade(
            tradeId: Long,
            type: TradeType,
            amount: BigDecimal,
            note: String,
            stakeAmount: BigDecimal?,
            payoutPercent: BigDecimal?
        ): AlertEvaluationResult = AlertEvaluationResult(null, false, false)

        override suspend fun deleteTrade(tradeId: Long) {
            trades.value = trades.value.filterNot { it.id == tradeId }
        }

        override suspend fun saveDailyReflection(date: LocalDate, reflectionText: String) {
            val existing = records.value[date]
            records.value = records.value + (
                date to (existing?.copy(reflectionText = reflectionText)
                    ?: DailyRecord(date = date, startingBalance = null, reflectionText = reflectionText))
            )
        }
    }

    class MainDispatcherRule(
        private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
