package com.nexora.trade.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "trades",
    indices = [
        Index(value = ["dateKey"]),
        Index(value = ["timestampMillis"])
    ]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateKey: String,
    val type: TradeType,
    val amount: BigDecimal,
    val stakeAmount: BigDecimal? = null,
    val payoutPercent: BigDecimal? = null,
    val note: String = "",
    val timestampMillis: Long
) {
    fun toDomain(): TradeEntry = TradeEntry(
        id = id,
        date = LocalDate.parse(dateKey),
        type = type,
        amount = amount,
        stakeAmount = stakeAmount,
        payoutPercent = payoutPercent,
        note = note,
        timestampMillis = timestampMillis
    )
}