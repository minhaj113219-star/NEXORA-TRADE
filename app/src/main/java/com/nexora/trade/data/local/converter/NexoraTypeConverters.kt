package com.nexora.trade.data.local.converter

import androidx.room.TypeConverter
import com.nexora.trade.domain.model.TradeType
import java.math.BigDecimal
import java.time.LocalDate

class NexoraTypeConverters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun fromTradeType(type: TradeType?): String? = type?.name

    @TypeConverter
    fun toTradeType(value: String?): TradeType? = value?.let(TradeType::valueOf)
}