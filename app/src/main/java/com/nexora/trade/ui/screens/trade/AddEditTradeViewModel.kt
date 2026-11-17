package com.nexora.trade.ui.screens.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val noteInput: String = "",
    val amountError: String? = null,
    val pendingConfirmAmount: BigDecimal? = null,
    val isSaving: Boolean = false,
    val triggeredAlert: DailyAlertType? = null,
    val tradeNotFound: Boolean = false,
    val shouldNavigateBack: Boolean = false
)

class AddEditTradeViewModel(
    private val repository: TradingRepository,
    private val date: LocalDate,
    private val tradeId: Long,
    private val alertManager: TradingAlertManager = NoOpTradingAlertManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditTradeUiState(
            isLoading = true,
            isEditMode = tradeId > 0L,
            date = date
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = true,
                            date = existing.date,
                            selectedType = existing.type,
                            amountInput = existing.amount.stripTrailingZeros().toPlainString(),
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
        _uiState.update { it.copy(selectedType = type) }
    }

    fun updateAmountInput(rawAmount: String) {
        _uiState.update {
            it.copy(
                amountInput = rawAmount,
                amountError = null
            )
        }
    }

    fun updateNoteInput(rawNote: String) {
        val clipped = rawNote.take(120)
        _uiState.update { it.copy(noteInput = clipped) }
    }

    fun requestSaveConfirmation() {
        val current = _uiState.value
        val parsed = MoneyCalculator.parsePositiveAmount(current.amountInput)
        if (parsed == null) {
            _uiState.update {
                it.copy(amountError = "Enter a valid positive amount (e.g., 10 or 12.50).")
            }
            return
        }
        _uiState.update { it.copy(pendingConfirmAmount = parsed) }
    }

    fun dismissSaveConfirmation() {
        _uiState.update { it.copy(pendingConfirmAmount = null) }
    }

    fun confirmSaveTrade() {
        val current = _uiState.value
        val amount = current.pendingConfirmAmount ?: return
        if (current.isSaving) return

        _uiState.update {
            it.copy(
                isSaving = true,
                pendingConfirmAmount = null
            )
        }

        viewModelScope.launch {
            val alertResult = if (current.isEditMode && tradeId > 0L) {
                repository.updateTrade(
                    tradeId = tradeId,
                    type = current.selectedType,
                    amount = amount,
                    note = current.noteInput
                )
            } else {
                repository.addTrade(
                    date = current.date,
                    type = current.selectedType,
                    amount = amount,
                    note = current.noteInput
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
            alertManager: TradingAlertManager
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AddEditTradeViewModel::class.java)) {
                        return AddEditTradeViewModel(repository, date, tradeId, alertManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}