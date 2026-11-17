package com.nexora.trade.ui.screens.thirtyday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.ThirtyDayAggregator
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.ThirtyDayDayItem
import com.nexora.trade.domain.model.ThirtyDaySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class ThirtyDayFilter(val label: String) {
    ALL_DAYS("All 30 Days"),
    RECORDED_ONLY("Recorded"),
    TARGETS_HIT("Target +10%"),
    LOSS_LIMITS("Loss −5%")
}

data class ThirtyDayUiState(
    val isLoading: Boolean = true,
    val summary: ThirtyDaySummary = ThirtyDayAggregator.buildSummary(
        endDate = LocalDate.now(),
        recordsByDate = emptyMap(),
        tradesByDate = emptyMap()
    ),
    val selectedFilter: ThirtyDayFilter = ThirtyDayFilter.ALL_DAYS,
    val filteredDays: List<ThirtyDayDayItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ThirtyDayViewModel(
    private val repository: TradingRepository,
    private val dateProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val endDate = MutableStateFlow(dateProvider())
    private val selectedFilter = MutableStateFlow(ThirtyDayFilter.ALL_DAYS)

    private val thirtyDayFlow = endDate.flatMapLatest { date ->
        repository.observeThirtyDaySummary(date)
    }

    val uiState: StateFlow<ThirtyDayUiState> = combine(
        thirtyDayFlow,
        selectedFilter
    ) { summary, filter ->
        val filtered = when (filter) {
            ThirtyDayFilter.ALL_DAYS -> summary.days
            ThirtyDayFilter.RECORDED_ONLY -> summary.days.filter { it.hasAnyRecord }
            ThirtyDayFilter.TARGETS_HIT -> summary.days.filter {
                it.summary.status == DailyStatus.PROFIT_TARGET_COMPLETED
            }
            ThirtyDayFilter.LOSS_LIMITS -> summary.days.filter {
                it.summary.status == DailyStatus.LOSS_LIMIT_REACHED
            }
        }

        ThirtyDayUiState(
            isLoading = false,
            summary = summary,
            selectedFilter = filter,
            filteredDays = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ThirtyDayUiState()
    )

    fun refreshDateIfChanged() {
        val current = dateProvider()
        if (endDate.value != current) {
            endDate.value = current
        }
    }

    fun selectFilter(filter: ThirtyDayFilter) {
        selectedFilter.value = filter
    }

    companion object {
        fun provideFactory(repository: TradingRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ThirtyDayViewModel::class.java)) {
                        return ThirtyDayViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}