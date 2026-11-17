package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.ThirtyDayDayItem
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.domain.model.TradeEntry
import java.math.BigDecimal
import java.time.LocalDate

object ThirtyDayAggregator {

    const val WINDOW_DAYS: Long = 30L

    fun buildSummary(
        endDate: LocalDate,
        recordsByDate: Map<LocalDate, DailyRecord>,
        tradesByDate: Map<LocalDate, List<TradeEntry>>
    ): ThirtyDaySummary {
        val startDate = endDate.minusDays(WINDOW_DAYS - 1L)

        val days = (0L until WINDOW_DAYS).map { offset ->
            val date = endDate.minusDays(offset)
            val record = recordsByDate[date]
            val dayTrades = tradesByDate[date].orEmpty()

            val hasStartingBalance = record?.startingBalance != null &&
                record.startingBalance > BigDecimal.ZERO
            val hasTrades = dayTrades.isNotEmpty()
            val hasReflection = !record?.reflectionText.isNullOrBlank()
            val hasAnyRecord = hasStartingBalance || hasTrades || hasReflection

            val summary = MoneyCalculator.calculateDailySummary(
                date = date,
                startingBalance = record?.startingBalance,
                trades = dayTrades
            )

            ThirtyDayDayItem(
                date = date,
                hasStartingBalance = hasStartingBalance,
                hasTrades = hasTrades,
                hasReflection = hasReflection,
                hasAnyRecord = hasAnyRecord,
                summary = summary
            )
        }

        val recordedDays = days.filter { it.hasAnyRecord }
        val totalTradesCount = days.sumOf { it.summary.tradeCount }
        val totalNetProfitLoss = MoneyCalculator.normalizeCurrency(
            days.fold(MoneyConstants.ZERO_CURRENCY) { acc, item ->
                acc.add(item.summary.netProfitLoss)
            }
        )

        val winningDaysCount = recordedDays.count {
            it.summary.netProfitLoss > BigDecimal.ZERO
        }
        val losingDaysCount = recordedDays.count {
            it.summary.netProfitLoss < BigDecimal.ZERO
        }
        val breakEvenDaysCount = recordedDays.count {
            it.summary.netProfitLoss.compareTo(BigDecimal.ZERO) == 0
        }
        val targetCompletedDaysCount = recordedDays.count {
            it.summary.status == DailyStatus.PROFIT_TARGET_COMPLETED
        }
        val lossLimitReachedDaysCount = recordedDays.count {
            it.summary.status == DailyStatus.LOSS_LIMIT_REACHED
        }

        return ThirtyDaySummary(
            startDate = startDate,
            endDate = endDate,
            days = days,
            recordedDaysCount = recordedDays.size,
            totalTradesCount = totalTradesCount,
            totalNetProfitLoss = totalNetProfitLoss,
            winningDaysCount = winningDaysCount,
            losingDaysCount = losingDaysCount,
            breakEvenDaysCount = breakEvenDaysCount,
            targetCompletedDaysCount = targetCompletedDaysCount,
            lossLimitReachedDaysCount = lossLimitReachedDaysCount
        )
    }
}