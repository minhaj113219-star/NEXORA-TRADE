package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ThirtyDayAggregatorTest {

    @Test
    fun `30 day summary keeps separate dates independent and distinguishes missing from zero days`() {
        val today = LocalDate.of(2026, 5, 30)
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)

        val records = mapOf(
            today to DailyRecord(
                date = today,
                startingBalance = BigDecimal("100.00")
            ),
            yesterday to DailyRecord(
                date = yesterday,
                startingBalance = BigDecimal("250.00")
            )
        )

        // Today: +10 profit; Yesterday: +5 profit and -5 loss = 0.00 break-even; Two days ago: no record
        val trades = mapOf(
            today to listOf(
                TradeEntry(1L, today, TradeType.PROFIT, BigDecimal("10.00"), "", 1000L)
            ),
            yesterday to listOf(
                TradeEntry(2L, yesterday, TradeType.PROFIT, BigDecimal("5.00"), "", 2000L),
                TradeEntry(3L, yesterday, TradeType.LOSS, BigDecimal("5.00"), "", 3000L)
            )
        )

        val result = ThirtyDayAggregator.buildSummary(
            endDate = today,
            recordsByDate = records,
            tradesByDate = trades
        )

        assertEquals(30, result.days.size)
        assertEquals(2, result.recordedDaysCount)
        assertEquals(3, result.totalTradesCount)
        assertEquals(0, BigDecimal("10.00").compareTo(result.totalNetProfitLoss))
        assertEquals(1, result.winningDaysCount)
        assertEquals(0, result.losingDaysCount)
        assertEquals(1, result.breakEvenDaysCount)
        assertEquals(1, result.targetCompletedDaysCount)
        assertEquals(0, result.lossLimitReachedDaysCount)

        val todayItem = result.days[0]
        val yesterdayItem = result.days[1]
        val missingItem = result.days[2]

        assertEquals(today, todayItem.date)
        assertTrue(todayItem.hasAnyRecord)
        assertEquals(0, BigDecimal("100.00").compareTo(todayItem.summary.startingBalance))

        assertEquals(yesterday, yesterdayItem.date)
        assertTrue(yesterdayItem.hasAnyRecord)
        assertTrue(yesterdayItem.hasTrades)
        assertEquals(0, BigDecimal("250.00").compareTo(yesterdayItem.summary.startingBalance))
        assertEquals(0, BigDecimal.ZERO.compareTo(yesterdayItem.summary.netProfitLoss))

        assertEquals(twoDaysAgo, missingItem.date)
        assertFalse(missingItem.hasAnyRecord)
        assertFalse(missingItem.hasTrades)
        assertFalse(missingItem.hasStartingBalance)
    }
}