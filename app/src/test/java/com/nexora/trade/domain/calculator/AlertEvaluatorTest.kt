package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AlertEvaluatorTest {

    private val testDate: LocalDate = LocalDate.of(2026, 5, 12)

    @Test
    fun `profit target triggers once on first reach and does not repeat on refresh`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(
                TradeEntry(1L, testDate, TradeType.PROFIT, BigDecimal("10"), "", 1000L)
            )
        )

        val firstEval = AlertEvaluator.evaluate(
            summary = summary,
            existingRecord = DailyRecord(date = testDate, startingBalance = BigDecimal("100"))
        )
        assertEquals(DailyAlertType.PROFIT_TARGET_COMPLETED, firstEval.alertToTrigger)
        assertTrue(firstEval.updatedTargetAlertTriggered)
        assertFalse(firstEval.updatedLossAlertTriggered)

        val refreshEval = AlertEvaluator.evaluate(
            summary = summary,
            existingRecord = DailyRecord(
                date = testDate,
                startingBalance = BigDecimal("100"),
                targetAlertTriggered = firstEval.updatedTargetAlertTriggered,
                lossAlertTriggered = firstEval.updatedLossAlertTriggered
            )
        )
        assertNull(refreshEval.alertToTrigger)
        assertTrue(refreshEval.updatedTargetAlertTriggered)
    }

    @Test
    fun `loss limit triggers once on first reach and does not repeat on refresh`() {
        val summary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(
                TradeEntry(1L, testDate, TradeType.LOSS, BigDecimal("5"), "", 1000L)
            )
        )

        val firstEval = AlertEvaluator.evaluate(
            summary = summary,
            existingRecord = DailyRecord(date = testDate, startingBalance = BigDecimal("100"))
        )
        assertEquals(DailyAlertType.LOSS_LIMIT_REACHED, firstEval.alertToTrigger)
        assertTrue(firstEval.updatedLossAlertTriggered)
        assertFalse(firstEval.updatedTargetAlertTriggered)

        val refreshEval = AlertEvaluator.evaluate(
            summary = summary,
            existingRecord = DailyRecord(
                date = testDate,
                startingBalance = BigDecimal("100"),
                targetAlertTriggered = firstEval.updatedTargetAlertTriggered,
                lossAlertTriggered = firstEval.updatedLossAlertTriggered
            )
        )
        assertNull(refreshEval.alertToTrigger)
        assertTrue(refreshEval.updatedLossAlertTriggered)
    }

    @Test
    fun `editing trade below threshold later does not create duplicate alerts`() {
        val editedSummary = MoneyCalculator.calculateDailySummary(
            date = testDate,
            startingBalance = BigDecimal("100"),
            trades = listOf(
                TradeEntry(1L, testDate, TradeType.PROFIT, BigDecimal("6"), "", 1000L)
            )
        )

        val afterEditEval = AlertEvaluator.evaluate(
            summary = editedSummary,
            existingRecord = DailyRecord(
                date = testDate,
                startingBalance = BigDecimal("100"),
                targetAlertTriggered = true,
                lossAlertTriggered = false
            )
        )

        assertNull(afterEditEval.alertToTrigger)
        assertTrue(afterEditEval.updatedTargetAlertTriggered)
    }
}