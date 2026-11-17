package com.nexora.trade.data.repository

import com.nexora.trade.data.local.dao.DailyRecordDao
import com.nexora.trade.data.local.dao.TradeDao
import com.nexora.trade.data.local.entity.DailyRecordEntity
import com.nexora.trade.data.local.entity.TradeEntity
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TradingRepositoryTest {

    private lateinit var fakeDailyRecordDao: InMemoryDailyRecordDao
    private lateinit var fakeTradeDao: InMemoryTradeDao
    private lateinit var repository: RoomTradingRepository

    private val today = LocalDate.of(2026, 5, 18)
    private val yesterday = today.minusDays(1)

    @Before
    fun setUp() {
        fakeDailyRecordDao = InMemoryDailyRecordDao()
        fakeTradeDao = InMemoryTradeDao()
        repository = RoomTradingRepository(
            dailyRecordDao = fakeDailyRecordDao,
            tradeDao = fakeTradeDao,
            transactionRunner = TransactionRunner { block -> block() },
            timeProvider = { 1700000000000L }
        )
    }

    @Test
    fun `different dates keep separate starting balances and totals`() = runTest {
        repository.setStartingBalance(today, BigDecimal("100.00"))
        repository.setStartingBalance(yesterday, BigDecimal("250.00"))

        repository.addTrade(today, TradeType.PROFIT, BigDecimal("6.00"), "Morning setup", 1000L)
        repository.addTrade(yesterday, TradeType.LOSS, BigDecimal("12.50"), "Stop hit", 2000L)

        val todaySnap = repository.observeDaySnapshot(today).first()
        val yesterdaySnap = repository.observeDaySnapshot(yesterday).first()

        assertEquals(0, BigDecimal("106.00").compareTo(todaySnap.summary.currentBalance))
        assertEquals(DailyStatus.IN_PROGRESS, todaySnap.summary.status)

        assertEquals(0, BigDecimal("237.50").compareTo(yesterdaySnap.summary.currentBalance))
        assertEquals(DailyStatus.LOSS_LIMIT_REACHED, yesterdaySnap.summary.status)
    }

    @Test
    fun `profit target alert persists triggered state and does not duplicate`() = runTest {
        repository.setStartingBalance(today, BigDecimal("100.00"))

        val firstAlert = repository.addTrade(today, TradeType.PROFIT, BigDecimal("10.00"), "Target", 1000L)
        assertEquals(DailyAlertType.PROFIT_TARGET_COMPLETED, firstAlert.alertToTrigger)
        assertTrue(firstAlert.updatedTargetAlertTriggered)

        val secondAlert = repository.addTrade(today, TradeType.PROFIT, BigDecimal("2.00"), "Extra", 2000L)
        assertNull(secondAlert.alertToTrigger)
        assertTrue(secondAlert.updatedTargetAlertTriggered)
    }

    @Test
    fun `updating and deleting trade recalculates day snapshot accurately`() = runTest {
        repository.setStartingBalance(today, BigDecimal("100.00"))
        repository.addTrade(today, TradeType.PROFIT, BigDecimal("8.00"), "Initial", 1000L)

        val insertedId = repository.observeDaySnapshot(today).first().trades.first().id

        repository.updateTrade(insertedId, TradeType.PROFIT, BigDecimal("10.00"), "Updated")
        val updatedSnap = repository.observeDaySnapshot(today).first()
        assertEquals(0, BigDecimal("10.00").compareTo(updatedSnap.summary.netProfitLoss))
        assertEquals(DailyStatus.PROFIT_TARGET_COMPLETED, updatedSnap.summary.status)
        assertEquals("Updated", updatedSnap.trades.first().note)

        repository.deleteTrade(insertedId)
        val afterDeleteSnap = repository.observeDaySnapshot(today).first()
        assertEquals(0, afterDeleteSnap.trades.size)
        assertEquals(0, BigDecimal("0.00").compareTo(afterDeleteSnap.summary.netProfitLoss))
        assertEquals(DailyStatus.IN_PROGRESS, afterDeleteSnap.summary.status)
    }

    @Test
    fun `saving reflection preserves starting balance and vice versa`() = runTest {
        repository.setStartingBalance(today, BigDecimal("200.00"))
        repository.saveDailyReflection(today, "Followed rules patiently.")

        var snapshot = repository.observeDaySnapshot(today).first()
        assertEquals(0, BigDecimal("200.00").compareTo(snapshot.summary.startingBalance))
        assertEquals("Followed rules patiently.", snapshot.record?.reflectionText)

        repository.setStartingBalance(today, BigDecimal("300.00"))
        snapshot = repository.observeDaySnapshot(today).first()
        assertEquals(0, BigDecimal("300.00").compareTo(snapshot.summary.startingBalance))
        assertEquals("Followed rules patiently.", snapshot.record?.reflectionText)
    }

    @Test
    fun `thirty day summary aggregates real persisted days only`() = runTest {
        repository.setStartingBalance(today, BigDecimal("100.00"))
        repository.addTrade(today, TradeType.PROFIT, BigDecimal("10.00"), "", 1000L)

        val thirtyDay = repository.observeThirtyDaySummary(today).first()
        assertEquals(30, thirtyDay.days.size)
        assertEquals(1, thirtyDay.recordedDaysCount)
        assertEquals(1, thirtyDay.totalTradesCount)
        assertEquals(0, BigDecimal("10.00").compareTo(thirtyDay.totalNetProfitLoss))
    }

    private class InMemoryDailyRecordDao : DailyRecordDao {
        private val store = MutableStateFlow<Map<String, DailyRecordEntity>>(emptyMap())

        override fun observeRecord(dateKey: String): Flow<DailyRecordEntity?> =
            store.map { it[dateKey] }

        override suspend fun getRecord(dateKey: String): DailyRecordEntity? =
            store.value[dateKey]

        override fun observeRecordsInRange(
            startDateKey: String,
            endDateKey: String
        ): Flow<List<DailyRecordEntity>> = store.map { map ->
            map.values
                .filter { it.dateKey in startDateKey..endDateKey }
                .sortedByDescending { it.dateKey }
        }

        override suspend fun upsert(entity: DailyRecordEntity) {
            store.value = store.value + (entity.dateKey to entity)
        }
    }

    private class InMemoryTradeDao : TradeDao {
        private val store = MutableStateFlow<List<TradeEntity>>(emptyList())
        private var nextId = 1L

        override fun observeTradesForDate(dateKey: String): Flow<List<TradeEntity>> =
            store.map { list ->
                list.filter { it.dateKey == dateKey }
                    .sortedWith(compareByDescending<TradeEntity> { it.timestampMillis }.thenByDescending { it.id })
            }

        override suspend fun getTradesForDate(dateKey: String): List<TradeEntity> =
            store.value.filter { it.dateKey == dateKey }
                .sortedWith(compareByDescending<TradeEntity> { it.timestampMillis }.thenByDescending { it.id })

        override fun observeTradesInRange(
            startDateKey: String,
            endDateKey: String
        ): Flow<List<TradeEntity>> = store.map { list ->
            list.filter { it.dateKey in startDateKey..endDateKey }
                .sortedWith(
                    compareByDescending<TradeEntity> { it.dateKey }
                        .thenByDescending { it.timestampMillis }
                        .thenByDescending { it.id }
                )
        }

        override fun observeAllTrades(): Flow<List<TradeEntity>> = store.map { list ->
            list.sortedWith(
                compareByDescending<TradeEntity> { it.dateKey }
                    .thenByDescending { it.timestampMillis }
                    .thenByDescending { it.id }
            )
        }

        override suspend fun getTradeById(tradeId: Long): TradeEntity? =
            store.value.firstOrNull { it.id == tradeId }

        override suspend fun insert(trade: TradeEntity): Long {
            val assignedId = nextId++
            store.value = store.value + trade.copy(id = assignedId)
            return assignedId
        }

        override suspend fun update(trade: TradeEntity) {
            store.value = store.value.map { if (it.id == trade.id) trade else it }
        }

        override suspend fun deleteById(tradeId: Long) {
            store.value = store.value.filterNot { it.id == tradeId }
        }
    }
}