package com.nexora.trade.ui.screens.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.TradeEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DailyTradeDetailsUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val summary: DailyCalculationSummary = MoneyCalculator.calculateDailySummary(
        date = LocalDate.now(),
        startingBalance = null,
        trades = emptyList()
    ),
    val trades: List<TradeEntry> = emptyList(),
    val reflectionText: String = "",
    val tradePendingDelete: TradeEntry? = null
)

class DailyTradeDetailsViewModel(
    private val repository: TradingRepository,
    private val date: LocalDate
) : ViewModel() {

    private val tradePendingDelete = MutableStateFlow<TradeEntry?>(null)

    val uiState: StateFlow<DailyTradeDetailsUiState> = combine(
        repository.observeDaySnapshot(date),
        tradePendingDelete
    ) { snapshot, pendingDelete ->
        DailyTradeDetailsUiState(
            isLoading = false,
            date = snapshot.date,
            summary = snapshot.summary,
            trades = snapshot.trades,
            reflectionText = snapshot.record?.reflectionText.orEmpty(),
            tradePendingDelete = pendingDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DailyTradeDetailsUiState(date = date)
    )

    fun requestDeleteTrade(trade: TradeEntry) {
        tradePendingDelete.value = trade
    }

    fun dismissDeleteDialog() {
        tradePendingDelete.value = null
    }

    fun confirmDeleteTrade() {
        val target = tradePendingDelete.value ?: return
        tradePendingDelete.value = null
        viewModelScope.launch {
            repository.deleteTrade(target.id)
        }
    }

    companion object {
        fun provideFactory(
            repository: TradingRepository,
            date: LocalDate
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DailyTradeDetailsViewModel::class.java)) {
                        return DailyTradeDetailsViewModel(repository, date) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}