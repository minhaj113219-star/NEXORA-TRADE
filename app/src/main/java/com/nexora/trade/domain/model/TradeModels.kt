package com.nexora.trade.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class TradeType {
    PROFIT,
    LOSS
}

data class TradeEntry(
    val id: Long = 0L,
    val date: LocalDate,
    val type: TradeType,
    val amount: BigDecimal,
    val note: String = "",
    val timestampMillis: Long
)

data class DailyRecord(
    val date: LocalDate,
    val startingBalance: BigDecimal?,
    val targetAlertTriggered: Boolean = false,
    val lossAlertTriggered: Boolean = false,
    val reflectionText: String = "",
    val updatedAtMillis: Long = 0L
)

enum class DailyStatus {
    NO_STARTING_BALANCE,
    IN_PROGRESS,
    PROFIT_TARGET_COMPLETED,
    LOSS_LIMIT_REACHED
}

data class DailyCalculationSummary(
    val date: LocalDate,
    val hasValidStartingBalance: Boolean,
    val startingBalance: BigDecimal,
    val dailyProfitTarget: BigDecimal,
    val targetBalance: BigDecimal,
    val maxDailyLoss: BigDecimal,
    val stopTradingBalance: BigDecimal,
    val netProfitLoss: BigDecimal,
    val currentBalance: BigDecimal,
    val profitPercentage: BigDecimal,
    val targetProgressPercent: BigDecimal,
    val targetProgressFraction: Float,
    val remainingToTarget: BigDecimal,
    val remainingLossAllowance: BigDecimal,
    val tradeCount: Int,
    val winningTradeCount: Int,
    val losingTradeCount: Int,
    val status: DailyStatus
)

enum class DailyAlertType {
    PROFIT_TARGET_COMPLETED,
    LOSS_LIMIT_REACHED
}

data class AlertEvaluationResult(
    val alertToTrigger: DailyAlertType?,
    val updatedTargetAlertTriggered: Boolean,
    val updatedLossAlertTriggered: Boolean
)

data class ThirtyDayDayItem(
    val date: LocalDate,
    val hasStartingBalance: Boolean,
    val hasTrades: Boolean,
    val hasReflection: Boolean,
    val hasAnyRecord: Boolean,
    val summary: DailyCalculationSummary
)

data class ThirtyDaySummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<ThirtyDayDayItem>,
    val recordedDaysCount: Int,
    val totalTradesCount: Int,
    val totalNetProfitLoss: BigDecimal,
    val winningDaysCount: Int,
    val losingDaysCount: Int,
    val breakEvenDaysCount: Int,
    val targetCompletedDaysCount: Int,
    val lossLimitReachedDaysCount: Int
)