package com.nexora.trade.alerts

import com.nexora.trade.data.local.dao.DailyRecordDao
import com.nexora.trade.data.local.dao.TradeDao
import com.nexora.trade.data.local.entity.DailyRecordEntity
import com.nexora.trade.data.local.entity.TradeEntity
import com.nexora.trade.data.repository.RoomTradingRepository
import com.nexora.trade.data.repository.TransactionRunner
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.screens.dashboard.DashboardViewModelTest.MainDispatcherRule
import com.nexora.trade.ui.screens.trade.AddEditTradeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AlertThresholdIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dayOne = LocalDate.of(2026, 6, 1)
    private val dayTwo = LocalDate.of(2026, 6, 2)

    @Test
    fun `alert manager dispatches once when profit target reached and ignores subsequent trades`() = runTest {
        val repo = createRepository()
        val spyAlertManager = SpyTradingAlertManager()

        repo.setStartingBalance(dayOne, BigDecimal("100.00"))

        // Trade 1: +6 -> still in progress, no alert
        val vm1 = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        vm1.selectTradeType(TradeType.PROFIT)
        vm1.updateAmountInput("6")
        vm1.requestSaveConfirmation()
        vm1.confirmSaveTrade()
        assertEquals(0, spyAlertManager.dispatchedAlerts.size)

        // Trade 2: +4 (total +10 = +10%) -> triggers PROFIT_TARGET_COMPLETED once
        val vm2 = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        vm2.selectTradeType(TradeType.PROFIT)
        vm2.updateAmountInput("4")
        vm2.requestSaveConfirmation()
        vm2.confirmSaveTrade()
        assertEquals(
            listOf(DailyAlertType.PROFIT_TARGET_COMPLETED to dayOne),
            spyAlertManager.dispatchedAlerts
        )

        // Trade 3: +3 (total +13) -> does not trigger duplicate alert
        val vm3 = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        vm3.selectTradeType(TradeType.PROFIT)
        vm3.updateAmountInput("3")
        vm3.requestSaveConfirmation()
        vm3.confirmSaveTrade()
        assertEquals(1, spyAlertManager.dispatchedAlerts.size)
    }

    @Test
    fun `loss limit triggers once on day one and next calendar day evaluates independently`() = runTest {
        val repo = createRepository()
        val spyAlertManager = SpyTradingAlertManager()

        repo.setStartingBalance(dayOne, BigDecimal("250.00"))
        repo.setStartingBalance(dayTwo, BigDecimal("100.00"))

        // Day 1: -12.50 (-5%) -> triggers LOSS_LIMIT_REACHED once
        val dayOneVm = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        dayOneVm.selectTradeType(TradeType.LOSS)
        dayOneVm.updateAmountInput("12.50")
        dayOneVm.requestSaveConfirmation()
        dayOneVm.confirmSaveTrade()

        // Day 1 extra loss -> does not duplicate
        val dayOneExtraVm = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        dayOneExtraVm.selectTradeType(TradeType.LOSS)
        dayOneExtraVm.updateAmountInput("2.00")
        dayOneExtraVm.requestSaveConfirmation()
        dayOneExtraVm.confirmSaveTrade()

        // Day 2: +10 (+10%) -> independent date triggers PROFIT_TARGET_COMPLETED once
        val dayTwoVm = AddEditTradeViewModel(repo, dayTwo, -1L, spyAlertManager)
        dayTwoVm.selectTradeType(TradeType.PROFIT)
        dayTwoVm.updateAmountInput("10")
        dayTwoVm.requestSaveConfirmation()
        dayTwoVm.confirmSaveTrade()

        assertEquals(
            listOf(
                DailyAlertType.LOSS_LIMIT_REACHED to dayOne,
                DailyAlertType.PROFIT_TARGET_COMPLETED to dayTwo
            ),
            spyAlertManager.dispatchedAlerts
        )
    }

    @Test
    fun `editing trade below threshold updates status accurately without duplicate alert`() = runTest {
        val repo = createRepository()
        val spyAlertManager = SpyTradingAlertManager()

        repo.setStartingBalance(dayOne, BigDecimal("100.00"))

        val addVm = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        addVm.selectTradeType(TradeType.PROFIT)
        addVm.updateAmountInput("10")
        addVm.requestSaveConfirmation()
        addVm.confirmSaveTrade()

        val savedTradeId = repo.observeDaySnapshot(dayOne).first().trades.first().id

        // Edit trade down to +6 -> status becomes IN_PROGRESS, no extra alert
        val editVm = AddEditTradeViewModel(repo, dayOne, savedTradeId, spyAlertManager)
        editVm.selectTradeType(TradeType.PROFIT)
        editVm.updateAmountInput("6")
        editVm.requestSaveConfirmation()
        editVm.confirmSaveTrade()

        val snapAfterEdit = repo.observeDaySnapshot(dayOne).first()
        assertEquals(DailyStatus.IN_PROGRESS, snapAfterEdit.summary.status)
        assertEquals(1, spyAlertManager.dispatchedAlerts.size)

        // Re-add +4 -> still no duplicate alert on the same day
        val addBackVm = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        addBackVm.selectTradeType(TradeType.PROFIT)
        addBackVm.updateAmountInput("4")
        addBackVm.requestSaveConfirmation()
        addBackVm.confirmSaveTrade()

        val snapFinal = repo.observeDaySnapshot(dayOne).first()
        assertEquals(DailyStatus.PROFIT_TARGET_COMPLETED, snapFinal.summary.status)
        assertEquals(1, spyAlertManager.dispatchedAlerts.size)
    }

    private fun createRepository(): RoomTradingRepository {
        return RoomTradingRepository(
            dailyRecordDao = TestDailyRecordDao(),
            tradeDao = TestTradeDao(),
            transactionRunner = TransactionRunner { block -> block() },
            timeProvider = { 1700000000000L }
        )
    }

    private class SpyTradingAlertManager : TradingAlertManager {
        val dispatchedAlerts = mutableListOf<Pair<DailyAlertType, LocalDate>>()

        override fun dispatchThresholdAlert(alertType: DailyAlertType, date: LocalDate) {
            dispatchedAlerts.add(alertType to date)
        }
    }

    private class TestDailyRecordDao : DailyRecordDao {
        private val store = MutableStateFlow<Map<String, DailyRecordEntity>>(emptyMap())

        override fun observeRecord(dateKey: String): Flow<DailyRecordEntity?> =
            store.map { it[dateKey] }

        override suspend fun getRecord(dateKey: String): DailyRecordEntity? =
            store.value[dateKey]

        override fun observeRecordsInRange(
            startDateKey: String,
            endDateKey: String
        ): Flow<List<DailyRecordEntity>> = store.map { map ->
            map.values.filter { it.dateKey in startDateKey..endDateKey }
        }

        override suspend fun upsert(entity: DailyRecordEntity) {
            store.value = store.value + (entity.dateKey to entity)
        }
    }

    private class TestTradeDao : TradeDao {
        private val store = MutableStateFlow<List<TradeEntity>>(emptyList())
        private var nextId = 1L

        override fun observeTradesForDate(dateKey: String): Flow<List<TradeEntity>> =
            store.map { list -> list.filter { it.dateKey == dateKey } }

        override suspend fun getTradesForDate(dateKey: String): List<TradeEntity> =
            store.value.filter { it.dateKey == dateKey }

        override fun observeTradesInRange(
            startDateKey: String,
            endDateKey: String
        ): Flow<List<TradeEntity>> =
            store.map { list -> list.filter { it.dateKey in startDateKey..endDateKey } }

        override fun observeAllTrades(): Flow<List<TradeEntity>> = store

        override suspend fun getTradeById(tradeId: Long): TradeEntity? =
            store.value.firstOrNull { it.id == tradeId }

        override suspend fun insert(trade: TradeEntity): Long {
            val id = nextId++
            store.value = store.value + trade.copy(id = id)
            return id
        }

        override suspend fun update(trade: TradeEntity) {
            store.value = store.value.map { if (it.id == trade.id) trade else it }
        }

        override suspend fun deleteById(tradeId: Long) {
            store.value = store.value.filterNot { it.id == tradeId }
        }
    }
}