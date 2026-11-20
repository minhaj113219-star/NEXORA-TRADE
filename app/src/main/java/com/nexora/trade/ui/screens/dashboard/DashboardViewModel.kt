package com.nexora.trade.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.alerts.NoOpTradingAlertManager
import com.nexora.trade.alerts.TradingAlertManager
import com.nexora.trade.data.repository.DaySnapshot
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.TradeEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

sealed interface StartingBalanceDialogState {
    data object Hidden : StartingBalanceDialogState

    data class Input(
        val rawAmount: String,
        val isEditingExisting: Boolean,
        val errorMessage: String? = null
    ) : StartingBalanceDialogState

    data class ConfirmEdit(
        val proposedBalance: BigDecimal
    ) : StartingBalanceDialogState
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val todayDate: LocalDate = LocalDate.now(),
    val summary: DailyCalculationSummary = MoneyCalculator.calculateDailySummary(
        date = LocalDate.now(),
        startingBalance = null,
        trades = emptyList()
    ),
    val todayTrades: List<TradeEntry> = emptyList(),
    val hasReflection: Boolean = false,
    val dialogState: StartingBalanceDialogState = StartingBalanceDialogState.Hidden,
    val activeAlertBanner: DailyAlertType? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: TradingRepository,
    private val alertManager: TradingAlertManager = NoOpTradingAlertManager,
    private val dateProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val activeDate = MutableStateFlow(dateProvider())
    private val dialogState = MutableStateFlow<StartingBalanceDialogState>(
        StartingBalanceDialogState.Hidden
    )
    private val activeAlertBanner = MutableStateFlow<DailyAlertType?>(null)

    private val daySnapshotFlow = activeDate.flatMapLatest { date ->
        repository.observeDaySnapshot(date)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        daySnapshotFlow,
        dialogState,
        activeAlertBanner
    ) { snapshot: DaySnapshot, dialog, alert ->
        DashboardUiState(
            isLoading = false,
            todayDate = snapshot.date,
            summary = snapshot.summary,
            todayTrades = snapshot.trades,
            hasReflection = !snapshot.record?.reflectionText.isNullOrBlank(),
            dialogState = dialog,
            activeAlertBanner = alert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(todayDate = activeDate.value)
    )

    init {
        syncCarryOverBalance(activeDate.value)
    }

    fun refreshDateIfChanged() {
        val current = dateProvider()
        if (activeDate.value != current) {
            activeDate.value = current
            activeAlertBanner.value = null
        }
        syncCarryOverBalance(current)
    }

    private fun syncCarryOverBalance(targetDate: LocalDate) {
        viewModelScope.launch {
            val alertResult = repository.ensureCarryOverStartingBalance(targetDate)
            val alertToTrigger = alertResult?.alertToTrigger
            if (alertToTrigger != null) {
                alertManager.dispatchThresholdAlert(alertToTrigger, targetDate)
                activeAlertBanner.update { alertToTrigger }
            }
        }
    }

    fun openStartingBalanceDialog() {
        val currentSummary = uiState.value.summary
        val initialText = if (currentSummary.hasValidStartingBalance) {
            currentSummary.startingBalance.stripTrailingZeros().toPlainString()
        } else {
            ""
        }
        dialogState.value = StartingBalanceDialogState.Input(
            rawAmount = initialText,
            isEditingExisting = currentSummary.hasValidStartingBalance,
            errorMessage = null
        )
    }

    fun updateStartingBalanceInput(newValue: String) {
        val current = dialogState.value
        if (current is StartingBalanceDialogState.Input) {
            dialogState.value = current.copy(
                rawAmount = newValue,
                errorMessage = null
            )
        }
    }

    fun submitStartingBalanceInput() {
        val current = dialogState.value as? StartingBalanceDialogState.Input ?: return
        val parsed = MoneyCalculator.parsePositiveAmount(current.rawAmount)
        if (parsed == null) {
            dialogState.value = current.copy(
                errorMessage = "Enter a valid positive amount (e.g., 100 or 250.50)."
            )
            return
        }

        if (current.isEditingExisting) {
            dialogState.value = StartingBalanceDialogState.ConfirmEdit(proposedBalance = parsed)
        } else {
            persistStartingBalance(parsed)
        }
    }

    fun confirmEditStartingBalance() {
        val confirmState = dialogState.value as? StartingBalanceDialogState.ConfirmEdit ?: return
        persistStartingBalance(confirmState.proposedBalance)
    }

    fun dismissStartingBalanceDialog() {
        dialogState.value = StartingBalanceDialogState.Hidden
    }

    fun dismissAlertBanner() {
        activeAlertBanner.value = null
    }

    private fun persistStartingBalance(balance: BigDecimal) {
        val targetDate = activeDate.value
        viewModelScope.launch {
            val alertResult = repository.setStartingBalance(targetDate, balance)
            dialogState.value = StartingBalanceDialogState.Hidden
            val alertToTrigger = alertResult.alertToTrigger
            if (alertToTrigger != null) {
                alertManager.dispatchThresholdAlert(alertToTrigger, targetDate)
                activeAlertBanner.update { alertToTrigger }
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: TradingRepository,
            alertManager: TradingAlertManager
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                        return DashboardViewModel(repository, alertManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}