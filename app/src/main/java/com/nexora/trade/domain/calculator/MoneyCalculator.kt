package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

object MoneyCalculator {

    private val POSITIVE_AMOUNT_REGEX = Regex("^\\d+(\\.\\d{1,2})?$")

    fun normalizeCurrency(value: BigDecimal): BigDecimal {
        return value.setScale(MoneyConstants.CURRENCY_SCALE, MoneyConstants.CURRENCY_ROUNDING)
    }

    fun parsePositiveAmount(rawInput: String): BigDecimal? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || !POSITIVE_AMOUNT_REGEX.matches(trimmed)) {
            return null
        }
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        if (parsed <= BigDecimal.ZERO) {
            return null
        }
        return normalizeCurrency(parsed)
    }

    fun calculateSignedTradeAmount(trade: TradeEntry): BigDecimal {
        val positiveMagnitude = normalizeCurrency(trade.amount.abs())
        return when (trade.type) {
            TradeType.PROFIT -> positiveMagnitude
            TradeType.LOSS -> positiveMagnitude.negate()
        }
    }

    fun calculateDailyProfitTarget(startingBalance: BigDecimal): BigDecimal {
        if (startingBalance <= BigDecimal.ZERO) {
            return MoneyConstants.ZERO_CURRENCY
        }
        return normalizeCurrency(
            startingBalance.multiply(MoneyConstants.PROFIT_TARGET_RATE)
        )
    }

    fun calculateMaxDailyLoss(startingBalance: BigDecimal): BigDecimal {
        if (startingBalance <= BigDecimal.ZERO) {
            return MoneyConstants.ZERO_CURRENCY
        }
        return normalizeCurrency(
            startingBalance.multiply(MoneyConstants.LOSS_LIMIT_RATE)
        )
    }

    fun calculateDailySummary(
        date: LocalDate,
        startingBalance: BigDecimal?,
        trades: List<TradeEntry>
    ): DailyCalculationSummary {
        val netProfitLoss = normalizeCurrency(
            trades.fold(MoneyConstants.ZERO_CURRENCY) { acc, trade ->
                acc.add(calculateSignedTradeAmount(trade))
            }
        )
        val winningTradeCount = trades.count {
            it.type == TradeType.PROFIT && it.amount > BigDecimal.ZERO
        }
        val losingTradeCount = trades.count {
            it.type == TradeType.LOSS && it.amount > BigDecimal.ZERO
        }

        val hasValidStartingBalance = startingBalance != null && startingBalance > BigDecimal.ZERO
        if (!hasValidStartingBalance) {
            return DailyCalculationSummary(
                date = date,
                hasValidStartingBalance = false,
                startingBalance = MoneyConstants.ZERO_CURRENCY,
                dailyProfitTarget = MoneyConstants.ZERO_CURRENCY,
                targetBalance = MoneyConstants.ZERO_CURRENCY,
                maxDailyLoss = MoneyConstants.ZERO_CURRENCY,
                stopTradingBalance = MoneyConstants.ZERO_CURRENCY,
                netProfitLoss = netProfitLoss,
                currentBalance = netProfitLoss,
                profitPercentage = MoneyConstants.ZERO_CURRENCY,
                targetProgressPercent = MoneyConstants.ZERO_CURRENCY,
                targetProgressFraction = 0f,
                remainingToTarget = MoneyConstants.ZERO_CURRENCY,
                remainingLossAllowance = MoneyConstants.ZERO_CURRENCY,
                tradeCount = trades.size,
                winningTradeCount = winningTradeCount,
                losingTradeCount = losingTradeCount,
                status = DailyStatus.NO_STARTING_BALANCE
            )
        }

        val safeStarting = normalizeCurrency(startingBalance!!)
        val dailyProfitTarget = calculateDailyProfitTarget(safeStarting)
        val targetBalance = normalizeCurrency(safeStarting.add(dailyProfitTarget))
        val maxDailyLoss = calculateMaxDailyLoss(safeStarting)
        val stopTradingBalance = normalizeCurrency(safeStarting.subtract(maxDailyLoss))
        val currentBalance = normalizeCurrency(safeStarting.add(netProfitLoss))

        val profitPercentage = netProfitLoss
            .multiply(MoneyConstants.HUNDRED)
            .divide(safeStarting, MoneyConstants.CURRENCY_SCALE, MoneyConstants.CURRENCY_ROUNDING)

        val rawTargetProgress = if (dailyProfitTarget > BigDecimal.ZERO && netProfitLoss > BigDecimal.ZERO) {
            netProfitLoss
                .multiply(MoneyConstants.HUNDRED)
                .divide(dailyProfitTarget, MoneyConstants.CURRENCY_SCALE, MoneyConstants.CURRENCY_ROUNDING)
        } else {
            MoneyConstants.ZERO_CURRENCY
        }

        val clampedProgressPercent = rawTargetProgress
            .min(MoneyConstants.HUNDRED)
            .max(MoneyConstants.ZERO_CURRENCY)

        val targetProgressFraction = clampedProgressPercent
            .divide(MoneyConstants.HUNDRED, 4, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)

        val remainingToTarget = if (netProfitLoss >= dailyProfitTarget) {
            MoneyConstants.ZERO_CURRENCY
        } else {
            normalizeCurrency(dailyProfitTarget.subtract(netProfitLoss))
        }

        val rawAllowance = normalizeCurrency(netProfitLoss.add(maxDailyLoss))
        val remainingLossAllowance = if (rawAllowance > BigDecimal.ZERO) {
            rawAllowance
        } else {
            MoneyConstants.ZERO_CURRENCY
        }

        val negativeLossThreshold = maxDailyLoss.negate()
        val status = when {
            netProfitLoss >= dailyProfitTarget -> DailyStatus.PROFIT_TARGET_COMPLETED
            netProfitLoss <= negativeLossThreshold -> DailyStatus.LOSS_LIMIT_REACHED
            else -> DailyStatus.IN_PROGRESS
        }

        return DailyCalculationSummary(
            date = date,
            hasValidStartingBalance = true,
            startingBalance = safeStarting,
            dailyProfitTarget = dailyProfitTarget,
            targetBalance = targetBalance,
            maxDailyLoss = maxDailyLoss,
            stopTradingBalance = stopTradingBalance,
            netProfitLoss = netProfitLoss,
            currentBalance = currentBalance,
            profitPercentage = profitPercentage,
            targetProgressPercent = clampedProgressPercent,
            targetProgressFraction = targetProgressFraction,
            remainingToTarget = remainingToTarget,
            remainingLossAllowance = remainingLossAllowance,
            tradeCount = trades.size,
            winningTradeCount = winningTradeCount,
            losingTradeCount = losingTradeCount,
            status = status
        )
    }

    fun formatAmount(amount: BigDecimal, includePlusSign: Boolean = false): String {
        val normalized = normalizeCurrency(amount)
        val symbols = DecimalFormatSymbols(Locale.US)
        val formatter = DecimalFormat("#,##0.00", symbols)
        val absFormatted = formatter.format(normalized.abs())
        return when {
            normalized < BigDecimal.ZERO -> "−$absFormatted"
            normalized > BigDecimal.ZERO && includePlusSign -> "+$absFormatted"
            else -> absFormatted
        }
    }

    fun formatPercent(percent: BigDecimal, includePlusSign: Boolean = false): String {
        return "${formatAmount(percent, includePlusSign = includePlusSign)}%"
    }
}