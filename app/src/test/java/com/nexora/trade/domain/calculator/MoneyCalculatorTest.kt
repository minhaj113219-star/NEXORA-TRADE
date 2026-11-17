package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MoneyCalculatorTest {

    private val testDate: LocalDate = LocalDate.of(2026, 5, 12)

    private fun profitTrade(id: Long, amount: String, date: LocalDate = testDate) = TradeEntry(
        id = id,
        date = date,
        type = TradeType.PROFIT,
        amount = BigDecimal(amount),
        timestampMillis = id * 1000L
    )

    private fun lossTrade(id: Long, amount: String, date: LocalDate = testDate) = TradeEntry(
        id = id,
        date = date,
        type = TradeType.LOSS,
        amount = BigDecimal(amount),
        timestampMillis = id * 1000L
    )

    private fun assertMoneyEquals(expected: String, actual: BigDecimal) {
        assertEquals(
            "Expected $expected but got $actual",
            0,
            BigDecimal(expected).compareTo(actual)
        )
    }

    @Test
    fun `example 1 fixed 10 percent target and 5 percent loss limit from starting balance 100`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = emptyList()
        )

        assertTrue(summary.hasValidStartingBalance)
        assertMoneyEquals("100.00", summary.startingBalance)
        assertMoneyEquals("10.00", summary.dailyProfitTarget)
        assertMoneyEquals("110.00", summary.targetBalance)
        assertMoneyEquals("5.00", summary.maxDailyLoss)
        assertMoneyEquals("95.00", summary.stopTradingBalance)
        assertMoneyEquals("0.00", summary.netProfitLoss)
        assertMoneyEquals("100.00", summary.currentBalance)
        assertEquals(DailyStatus.IN_PROGRESS, summary.status)
    }

    @Test
    fun `example 2 fixed 10 percent target and 5 percent loss limit from starting balance 250`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("250"),
            trades = emptyList()
        )

        assertMoneyEquals("250.00", summary.startingBalance)
        assertMoneyEquals("25.00", summary.dailyProfitTarget)
        assertMoneyEquals("275.00", summary.targetBalance)
        assertMoneyEquals("12.50", summary.maxDailyLoss)
        assertMoneyEquals("237.50", summary.stopTradingBalance)
    }

    @Test
    fun `example 1 positive trade of 6 gives 60 percent target progress and in progress status`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(profitTrade(1L, "6"))
        )

        assertMoneyEquals("6.00", summary.netProfitLoss)
        assertMoneyEquals("106.00", summary.currentBalance)
        assertMoneyEquals("6.00", summary.profitPercentage)
        assertMoneyEquals("60.00", summary.targetProgressPercent)
        assertEquals(0.6f, summary.targetProgressFraction, 0.0001f)
        assertMoneyEquals("4.00", summary.remainingToTarget)
        assertMoneyEquals("11.00", summary.remainingLossAllowance)
        assertEquals(DailyStatus.IN_PROGRESS, summary.status)
    }

    @Test
    fun `example 1 negative trade updates balance and remaining loss allowance`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(lossTrade(1L, "3.25"))
        )

        assertMoneyEquals("-3.25", summary.netProfitLoss)
        assertMoneyEquals("96.75", summary.currentBalance)
        assertMoneyEquals("-3.25", summary.profitPercentage)
        assertMoneyEquals("0.00", summary.targetProgressPercent)
        assertMoneyEquals("1.75", summary.remainingLossAllowance)
        assertMoneyEquals("13.25", summary.remainingToTarget)
        assertEquals(DailyStatus.IN_PROGRESS, summary.status)
    }

    @Test
    fun `multiple trades on same day use decimal safe arithmetic and keep fixed thresholds`() {
        val trades = listOf(
            profitTrade(1L, "0.10"),
            profitTrade(2L, "0.20"),
            lossTrade(3L, "1.30"),
            profitTrade(4L, "7.00")
        )

        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = trades
        )

        assertMoneyEquals("6.00", summary.netProfitLoss)
        assertMoneyEquals("106.00", summary.currentBalance)
        assertMoneyEquals("10.00", summary.dailyProfitTarget)
        assertMoneyEquals("5.00", summary.maxDailyLoss)
        assertEquals(4, summary.tradeCount)
        assertEquals(3, summary.winningTradeCount)
        assertEquals(1, summary.losingTradeCount)
    }

    @Test
    fun `profit target reached exactly sets completed status`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(profitTrade(1L, "10"))
        )

        assertMoneyEquals("10.00", summary.netProfitLoss)
        assertMoneyEquals("110.00", summary.currentBalance)
        assertMoneyEquals("100.00", summary.targetProgressPercent)
        assertMoneyEquals("0.00", summary.remainingToTarget)
        assertEquals(DailyStatus.PROFIT_TARGET_COMPLETED, summary.status)
    }

    @Test
    fun `profit target exceeded clamps progress at 100 percent and keeps fixed target`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(profitTrade(1L, "15.50"))
        )

        assertMoneyEquals("15.50", summary.netProfitLoss)
        assertMoneyEquals("115.50", summary.currentBalance)
        assertMoneyEquals("15.50", summary.profitPercentage)
        assertMoneyEquals("10.00", summary.dailyProfitTarget)
        assertMoneyEquals("100.00", summary.targetProgressPercent)
        assertMoneyEquals("0.00", summary.remainingToTarget)
        assertEquals(DailyStatus.PROFIT_TARGET_COMPLETED, summary.status)
    }

    @Test
    fun `loss limit reached exactly sets loss limit reached status`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(lossTrade(1L, "5"))
        )

        assertMoneyEquals("-5.00", summary.netProfitLoss)
        assertMoneyEquals("95.00", summary.currentBalance)
        assertMoneyEquals("0.00", summary.remainingLossAllowance)
        assertEquals(DailyStatus.LOSS_LIMIT_REACHED, summary.status)
    }

    @Test
    fun `loss limit exceeded keeps loss limit status and zero remaining allowance`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(lossTrade(1L, "8.75"))
        )

        assertMoneyEquals("-8.75", summary.netProfitLoss)
        assertMoneyEquals("91.25", summary.currentBalance)
        assertMoneyEquals("0.00", summary.remainingLossAllowance)
        assertEquals(DailyStatus.LOSS_LIMIT_REACHED, summary.status)
    }

    @Test
    fun `zero or null starting balance avoids division by zero and marks no starting balance`() {
        val zeroSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal.ZERO,
            trades = listOf(profitTrade(1L, "10"))
        )
        val nullSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = null,
            trades = emptyList()
        )

        assertFalse(zeroSummary.hasValidStartingBalance)
        assertEquals(DailyStatus.NO_STARTING_BALANCE, zeroSummary.status)
        assertMoneyEquals("0.00", zeroSummary.profitPercentage)
        assertMoneyEquals("0.00", zeroSummary.dailyProfitTarget)

        assertFalse(nullSummary.hasValidStartingBalance)
        assertEquals(DailyStatus.NO_STARTING_BALANCE, nullSummary.status)
    }

    @Test
    fun `parsePositiveAmount rejects blank negative zero and malformed inputs`() {
        assertNull(MoneyCalculator.parsePositiveAmount(""))
        assertNull(MoneyCalculator.parsePositiveAmount("0"))
        assertNull(MoneyCalculator.parsePositiveAmount("0.00"))
        assertNull(MoneyCalculator.parsePositiveAmount("-50"))
        assertNull(MoneyCalculator.parsePositiveAmount("12.345"))
        assertNull(MoneyCalculator.parsePositiveAmount("abc"))

        assertMoneyEquals("125.50", MoneyCalculator.parsePositiveAmount("125.5")!!)
    }

    @Test
    fun `editing and deleting trades recalculates daily totals accurately`() {
        val initialTrades = mutableListOf(
            profitTrade(1L, "6.00"),
            profitTrade(2L, "4.00")
        )
        val reachedSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = initialTrades
        )
        assertEquals(DailyStatus.PROFIT_TARGET_COMPLETED, reachedSummary.status)

        // Edit second trade to 2.00
        initialTrades[1] = profitTrade(2L, "2.00")
        val editedSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = initialTrades
        )
        assertMoneyEquals("8.00", editedSummary.netProfitLoss)
        assertMoneyEquals("108.00", editedSummary.currentBalance)
        assertEquals(DailyStatus.IN_PROGRESS, editedSummary.status)

        // Delete first trade
        initialTrades.removeAt(0)
        val deletedSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = initialTrades
        )
        assertMoneyEquals("2.00", deletedSummary.netProfitLoss)
        assertMoneyEquals("102.00", deletedSummary.currentBalance)
        assertEquals(1, deletedSummary.tradeCount)
    }
}