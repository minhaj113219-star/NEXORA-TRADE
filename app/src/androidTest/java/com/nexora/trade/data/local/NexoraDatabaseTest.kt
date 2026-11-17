package com.nexora.trade.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexora.trade.data.local.dao.DailyRecordDao
import com.nexora.trade.data.local.dao.TradeDao
import com.nexora.trade.data.local.entity.DailyRecordEntity
import com.nexora.trade.data.local.entity.TradeEntity
import com.nexora.trade.domain.model.TradeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class NexoraDatabaseTest {

    private lateinit var database: NexoraDatabase
    private lateinit var dailyRecordDao: DailyRecordDao
    private lateinit var tradeDao: TradeDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NexoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dailyRecordDao = database.dailyRecordDao()
        tradeDao = database.tradeDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndQueryDailyRecordAndTradesPersistBigDecimalExactly() = runBlocking {
        val dateKey = "2026-05-18"
        dailyRecordDao.upsert(
            DailyRecordEntity(
                dateKey = dateKey,
                startingBalance = BigDecimal("250.00"),
                reflectionText = "Clean session"
            )
        )

        val tradeId = tradeDao.insert(
            TradeEntity(
                dateKey = dateKey,
                type = TradeType.LOSS,
                amount = BigDecimal("12.50"),
                note = "Risk stop",
                timestampMillis = 1700000000000L
            )
        )

        val loadedRecord = dailyRecordDao.getRecord(dateKey)
        assertNotNull(loadedRecord)
        assertEquals(BigDecimal("250.00"), loadedRecord?.startingBalance)
        assertEquals("Clean session", loadedRecord?.reflectionText)

        val trades = tradeDao.observeTradesForDate(dateKey).first()
        assertEquals(1, trades.size)
        assertEquals(tradeId, trades[0].id)
        assertEquals(TradeType.LOSS, trades[0].type)
        assertEquals(BigDecimal("12.50"), trades[0].amount)

        tradeDao.deleteById(tradeId)
        assertNull(tradeDao.getTradeById(tradeId))
    }
}