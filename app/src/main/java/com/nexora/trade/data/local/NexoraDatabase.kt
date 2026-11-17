package com.nexora.trade.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nexora.trade.data.local.converter.NexoraTypeConverters
import com.nexora.trade.data.local.dao.DailyRecordDao
import com.nexora.trade.data.local.dao.TradeDao
import com.nexora.trade.data.local.entity.DailyRecordEntity
import com.nexora.trade.data.local.entity.TradeEntity

@Database(
    entities = [
        DailyRecordEntity::class,
        TradeEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(NexoraTypeConverters::class)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun tradeDao(): TradeDao

    companion object {
        private const val DATABASE_NAME = "nexora_trade.db"

        @Volatile
        private var instance: NexoraDatabase? = null

        fun getInstance(context: Context): NexoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}