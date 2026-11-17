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
        assertEquals(listOf(DailyAlertType.PROFIT_TARGET_COMPLETED to dayOne), spyAlertManager.dispatchedAlerts)

        // Trade 3: +3 (total +13) -> does not trigger duplicate alert
        val vm3 = AddEditTradeViewModel(repo, dayOne, -1L, spyAlertManager)
        vm3.selectTradeType(TradeType.PROFIT)
        vm3.updateAmountInput("3")
        vm3.request