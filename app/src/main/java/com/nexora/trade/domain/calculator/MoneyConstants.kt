package com.nexora.trade.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyConstants {
    const val CURRENCY_SCALE: Int = 2
    val CURRENCY_ROUNDING: RoundingMode = RoundingMode.HALF_UP

    val ZERO_CURRENCY: BigDecimal = BigDecimal("0.00")
    val HUNDRED: BigDecimal = BigDecimal("100.00")

    /** Fixed daily profit target: +10% of starting balance */
    val PROFIT_TARGET_RATE: BigDecimal = BigDecimal("0.10")

    /** Fixed daily maximum loss limit: -5% of starting balance */
    val LOSS_LIMIT_RATE: BigDecimal = BigDecimal("0.05")
}