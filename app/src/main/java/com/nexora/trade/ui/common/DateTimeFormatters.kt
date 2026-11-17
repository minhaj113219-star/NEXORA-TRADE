package com.nexora.trade.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeFormatters {
    private val fullDateFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
    private val mediumDateFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val shortDayDateFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val timeFormatter =
        DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    fun formatFullDate(date: LocalDate): String = date.format(fullDateFormatter)

    fun formatMediumDate(date: LocalDate): String = date.format(mediumDateFormatter)

    fun formatShortDayDate(date: LocalDate): String = date.format(shortDayDateFormatter)

    fun formatTimeFromMillis(timestampMillis: Long): String {
        val localDateTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return localDateTime.format(timeFormatter)
    }
}