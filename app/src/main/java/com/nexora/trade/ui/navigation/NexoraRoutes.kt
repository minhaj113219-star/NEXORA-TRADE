package com.nexora.trade.ui.navigation

object NexoraRoutes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val THIRTY_DAY = "thirty_day"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val ARG_DATE = "date"
    const val ARG_TRADE_ID = "tradeId"

    const val DAILY_DETAILS_PATTERN = "daily_details/{$ARG_DATE}"
    const val ADD_EDIT_TRADE_PATTERN = "add_edit_trade/{$ARG_DATE}?$ARG_TRADE_ID={$ARG_TRADE_ID}"
    const val REFLECTION_PATTERN = "reflection/{$ARG_DATE}"

    fun dailyDetails(dateIso: String): String = "daily_details/$dateIso"

    fun addTrade(dateIso: String): String = "add_edit_trade/$dateIso?$ARG_TRADE_ID=-1"

    fun editTrade(dateIso: String, tradeId: Long): String =
        "add_edit_trade/$dateIso?$ARG_TRADE_ID=$tradeId"

    fun reflection(dateIso: String): String = "reflection/$dateIso"
}