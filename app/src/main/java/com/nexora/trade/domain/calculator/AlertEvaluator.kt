package com.nexora.trade.domain.calculator

import com.nexora.trade.domain.model.AlertEvaluationResult
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.DailyStatus

object AlertEvaluator {

    fun evaluate(
        summary: DailyCalculationSummary,
        existingRecord: DailyRecord?
    ): AlertEvaluationResult {
        if (!summary.hasValidStartingBalance) {
            return AlertEvaluationResult(
                alertToTrigger = null,
                updatedTargetAlertTriggered = false,
                updatedLossAlertTriggered = false
            )
        }

        val alreadyTriggeredTarget = existingRecord?.targetAlertTriggered ?: false
        val alreadyTriggeredLoss = existingRecord?.lossAlertTriggered ?: false

        return when (summary.status) {
            DailyStatus.PROFIT_TARGET_COMPLETED -> {
                val shouldTrigger = !alreadyTriggeredTarget
                AlertEvaluationResult(
                    alertToTrigger = if (shouldTrigger) {
                        DailyAlertType.PROFIT_TARGET_COMPLETED
                    } else {
                        null
                    },
                    updatedTargetAlertTriggered = true,
                    updatedLossAlertTriggered = alreadyTriggeredLoss
                )
            }

            DailyStatus.LOSS_LIMIT_REACHED -> {
                val shouldTrigger = !alreadyTriggeredLoss
                AlertEvaluationResult(
                    alertToTrigger = if (shouldTrigger) {
                        DailyAlertType.LOSS_LIMIT_REACHED
                    } else {
                        null
                    },
                    updatedTargetAlertTriggered = alreadyTriggeredTarget,
                    updatedLossAlertTriggered = true
                )
            }

            DailyStatus.IN_PROGRESS,
            DailyStatus.NO_STARTING_BALANCE -> {
                AlertEvaluationResult(
                    alertToTrigger = null,
                    updatedTargetAlertTriggered = alreadyTriggeredTarget,
                    updatedLossAlertTriggered = alreadyTriggeredLoss
                )
            }
        }
    }
}