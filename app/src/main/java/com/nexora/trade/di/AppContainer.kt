package com.nexora.trade.di

import android.content.Context
import com.nexora.trade.data.local.NexoraDatabase
import com.nexora.trade.data.repository.RoomTradingRepository
import com.nexora.trade.data.repository.RoomTransactionRunner
import com.nexora.trade.data.repository.TradingRepository

interface AppContainer {
    val database: NexoraDatabase
    val tradingRepository: TradingRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val database: NexoraDatabase by lazy {
        NexoraDatabase.getInstance(context)
    }

    override val tradingRepository: TradingRepository by lazy {
        RoomTradingRepository(
            dailyRecordDao = database.dailyRecordDao(),
            tradeDao = database.tradeDao(),
            transactionRunner = RoomTransactionRunner(database)
        )
    }
}