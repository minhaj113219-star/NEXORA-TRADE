package com.nexora.trade.di

import android.content.Context
import com.nexora.trade.alerts.AlertPreferences
import com.nexora.trade.alerts.AndroidTradingAlertManager
import com.nexora.trade.alerts.TradingAlertManager
import com.nexora.trade.data.local.NexoraDatabase
import com.nexora.trade.data.repository.RoomTradingRepository
import com.nexora.trade.data.repository.RoomTransactionRunner
import com.nexora.trade.data.repository.TradingRepository

interface AppContainer {
    val database: NexoraDatabase
    val tradingRepository: TradingRepository
    val alertPreferences: AlertPreferences
    val tradingAlertManager: TradingAlertManager
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val database: NexoraDatabase by lazy {
        NexoraDatabase.getInstance(appContext)
    }

    override val tradingRepository: TradingRepository by lazy {
        RoomTradingRepository(
            dailyRecordDao = database.dailyRecordDao(),
            tradeDao = database.tradeDao(),
            transactionRunner = RoomTransactionRunner(database)
        )
    }

    override val alertPreferences: AlertPreferences by lazy {
        AlertPreferences(appContext)
    }

    override val tradingAlertManager: TradingAlertManager by lazy {
        AndroidTradingAlertManager(
            context = appContext,
            alertPreferences = alertPreferences
        )
    }
}