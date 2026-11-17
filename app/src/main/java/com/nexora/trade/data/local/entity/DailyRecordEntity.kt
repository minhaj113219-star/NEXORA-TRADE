package com.nexora.trade.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.trade.domain.model.DailyRecord
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    @PrimaryKey
    val dateKey: String,
    val startingBalance: BigDecimal?,
    val targetAlertTriggered: Boolean = false,
    val lossAlertTriggered: Boolean = false,
    val reflectionText: String = "",
    val updatedAtMillis: Long = 0L
) {
    fun toDomain(): DailyRecord = DailyRecord(
        date = LocalDate.parse(dateKey),
        startingBalance = startingBalance,
        targetAlertTriggered = targetAlertTriggered,
        lossAlertTriggered = lossAlertTriggered,
        reflectionText = reflectionText,
        updatedAtMillis = updatedAtMillis
    )
}