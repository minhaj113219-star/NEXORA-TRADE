package com.nexora.trade.ui.screens.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.alerts.AppSettingsStore
import com.nexora.trade.alerts.NoOpTradingAlertManager
import com.nexora.trade.alerts.TradingAlertManager
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.TradeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class AddEditTradeUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val daySummary: DailyCalculationSummary = MoneyCalculator.calculateDailySummary(
        date = LocalDate.now(),
        startingBalance = null,
        trades = emptyList()
    ),
    val selectedType: TradeType = TradeType.PROFIT,
    val amountInput: String = "",
    val payoutInput: String = "85",
    val noteInput: String = "",
    val amountError: String? = null,
    val payoutError: String? = null,
    val pendingConfirmAmount: BigDecimal? = null,
    val pendingConfirmStake: BigDecimal? = null,
    val pendingConfirmPayout: BigDecimal? = null,
    val isSaving: Boolean = false,
    val triggeredAlert: DailyAlertType? = null,
    val tradeNotFound: Boolean = false,
    val shouldNavigateBack: Boolean = false
) {
    val previewNetAmount: BigDecimal?
        get() {
            val stake = MoneyCalculator.parsePositiveAmount(amountInput) ?: return null
            return when (selectedType) {
                TradeType.PROFIT -> {
                    val payout = MoneyCalculator.parsePayoutPercent(payoutInput) ?: return null
                    MoneyCalculator.calculateProfitFromPayout(stake, payout)
                }
                TradeType.LOSS -> stake
            }
        }
}

class AddEditTradeViewModel(
    private val repository: TradingRepository,
    private val date: LocalDate,
    private val tradeId: Long,
    private val alertManager: TradingAlertManager = NoOpTradingAlertManager,
    private val settingsStore: AppSettingsStore? = null
) : ViewModel() {

    private val defaultPayout = settingsStore?.getLastPayoutPercent() ?: "100"

    private val _uiState = MutableStateFlow(
        AddEditTradeUiState(
            isLoading = true,
            isEditMode = tradeId > 0L,
            date = date,
            payoutInput = defaultPayout
        )
    )
    val uiState: StateFlow<AddEditTradeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDaySnapshot(date).collect { snapshot ->
                _uiState.update { it.copy(daySummary = snapshot.summary) }
            }
        }

        viewModelScope.launch {
            if (tradeId > 0L) {
                val existing = repository.getTradeById(tradeId)
                if (existing == null) {
                    _uiState.update {
                        it.copy(isLoading = false, tradeNotFound = true)
                    }
                } else {
                    val stakeText = (existing.stakeAmount ?: existing.amount)
                        .stripTrailingZeros()
                        .toPlainString()
                    val payoutText = existing.payoutPercent
                        ?.stripTrailingZeros()
                        ?.toPlainString()
                        ?: if (existing.type == TradeType.PROFIT) "100" else defaultPayout

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = true,
                            date = existing.date,
                            selectedType = existing.type,
                            amountInput = stakeText,
                            payoutInput = payoutText,
                            noteInput = existing.note
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectTradeType(type: TradeType) {
        _uiState.update { it.copy(selectedType = type, payoutError = null) }
    }

    fun updateAmountInput(rawAmount: String) {
        _uiState.update {
            it.copy(
                amountInput = rawAmount,
                amountError = null
            )
        }
    }

    fun updatePayoutInput(rawPayout: String) {
        _uiState.update {
            it.copy(
                payoutInput = rawPayout,
                payoutError = null
            )
        }
    }

    fun updateNoteInput(rawNote: String) {
        val clipped = rawNote.take(120)
        _uiState.update { it.copy(noteInput = clipped) }
    }

    fun requestSaveConfirmation() {
        val current = _uiState.value
        val parsedStake = MoneyCalculator.parsePositiveAmount(current.amountInput)
        if (parsedStake == null) {
            _uiState.update {
                it.copy(amountError = "Enter a valid positive amount (e.g., 10 or 12.50).")
            }
            return
        }

        if (current.selectedType == TradeType.PROFIT) {
            val parsedPayout = MoneyCalculator.parsePayoutPercent(current.payoutInput)
            if (parsedPayout == null) {
                _uiState.update {
                    it.copy(payoutError = "Enter a valid payout % between 1 and 100 (e.g., 85).")
                }
                return
            }
            val netProfit = MoneyCalculator.calculateProfitFromPayout(parsedStake, parsedPayout)
            if (netProfit <= BigDecimal.ZERO) {
                _uiState.update {
                    it.copy(amountError = "Net profit must be greater than zero.")
                }
                return
            }
            _uiState.update {
                it.copy(
                    pendingConfirmAmount = netProfit,
                    pendingConfirmStake = parsedStake,
                    pendingConfirmPayout = parsedPayout
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    pendingConfirmAmount = parsedStake,
                    pendingConfirmStake = parsedStake,
                    pendingConfirmPayout = null
                )
            }
        }
    }

    fun dismissSaveConfirmation() {
        _uiState.update {
            it.copy(
                pendingConfirmAmount = null,
                pendingConfirmStake = null,
                pendingConfirmPayout = null
            )
        }
    }

    fun confirmSaveTrade() {
        val current = _uiState.value
        val netAmount = current.pendingConfirmAmount ?: return
        val stakeAmount = current.pendingConfirmStake
        val payoutPercent = current.pendingConfirmPayout
        if (current.isSaving) return

        if (current.selectedType == TradeType.PROFIT && payoutPercent != null) {
            settingsStore?.setLastPayoutPercent(payoutPercent.stripTrailingZeros().toPlainString())
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                pendingConfirmAmount = null,
                pendingConfirmStake = null,
                pendingConfirmPayout = null
            )
        }

        viewModelScope.launch {
            val alertResult = if (current.isEditMode && tradeId > 0L) {
                repository.updateTrade(
                    tradeId = tradeId,
                    type = current.selectedType,
                    amount = netAmount,
                    note = current.noteInput,
                    stakeAmount = stakeAmount,
                    payoutPercent = payoutPercent
                )
            } else {
                repository.addTrade(
                    date = current.date,
                    type = current.selectedType,
                    amount = netAmount,
                    note = current.noteInput,
                    stakeAmount = stakeAmount,
                    payoutPercent = payoutPercent
                )
            }

            val alertToTrigger = alertResult.alertToTrigger
            if (alertToTrigger != null) {
                alertManager.dispatchThresholdAlert(alertToTrigger, current.date)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        triggeredAlert = alertToTrigger
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        shouldNavigateBack = true
                    )
                }
            }
        }
    }

    fun dismissAlertAndFinish() {
        _uiState.update {
            it.copy(
                triggeredAlert = null,
                shouldNavigateBack = true
            )
        }
    }

    companion object {
        fun provideFactory(
            repository: TradingRepository,
            date: LocalDate,
            tradeId: Long,
            alertManager: TradingAlertManager,
            settingsStore: AppSettingsStore? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AddEditTradeViewModel::class.java)) {
                        return AddEditTradeViewModel(
                            repository = repository,
                            date = date,
                            tradeId = tradeId,
                            alertManager = alertManager,
                            settingsStore = settingsStore
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}