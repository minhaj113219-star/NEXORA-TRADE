package com.nexora.trade.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.calculator.MoneyConstants
import com.nexora.trade.domain.model.TradeEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate

data class TradeHistoryDayGroup(
    val date: LocalDate,
    val trades: List<TradeEntry>,
    val dayNetProfitLoss: BigDecimal
)

data class TradeHistoryUiState(
    val isLoading: Boolean = true,
    val totalTradesCount: Int = 0,
    val totalNetProfitLoss: BigDecimal = MoneyConstants.ZERO_CURRENCY,
    val dayGroups: List<TradeHistoryDayGroup> = emptyList()
)

class TradeHistoryViewModel(
    repository: TradingRepository
) : ViewModel() {

    val uiState: StateFlow<TradeHistoryUiState> = repository.observeAllTrades()
        .map { allTrades ->
            val groups = allTrades
                .groupBy { it.date }
                .entries
                .sortedByDescending { it.key }
                .map { (date, dayTrades) ->
                    val dayNet = MoneyCalculator.normalizeCurrency(
                        dayTrades.fold(MoneyConstants.ZERO_CURRENCY) { acc, trade ->
                            acc.add(MoneyCalculator.calculateSignedTradeAmount(trade))
                        }
                    )
                    TradeHistoryDayGroup(
                        date = date,
                        trades = dayTrades.sortedByDescending { it.timestampMillis },
                        dayNetProfitLoss = dayNet
                    )
                }

            val totalNet = MoneyCalculator.normalizeCurrency(
                allTrades.fold(MoneyConstants.ZERO_CURRENCY) { acc, trade ->
                    acc.add(MoneyCalculator.calculateSignedTradeAmount(trade))
                }
            )

            TradeHistoryUiState(
                isLoading = false,
                totalTradesCount = allTrades.size,
                totalNetProfitLoss = totalNet,
                dayGroups = groups
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TradeHistoryUiState()
        )

    companion object {
        fun provideFactory(repository: TradingRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TradeHistoryViewModel::class.java)) {
                        return TradeHistoryViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}