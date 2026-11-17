package com.nexora.trade.ui.screens.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexora.trade.data.repository.TradingRepository
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyCalculationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DailyReflectionUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val summary: DailyCalculationSummary = MoneyCalculator.calculateDailySummary(
        date = LocalDate.now(),
        startingBalance = null,
        trades = emptyList()
    ),
    val savedText: String = "",
    val reflectionInput: String = "",
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val shouldNavigateBack: Boolean = false
) {
    val hasUnsavedChanges: Boolean
        get() = reflectionInput != savedText
}

class DailyReflectionViewModel(
    private val repository: TradingRepository,
    private val date: LocalDate
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DailyReflectionUiState(
            isLoading = true,
            date = date
        )
    )
    val uiState: StateFlow<DailyReflectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val initialSnapshot = repository.observeDaySnapshot(date).first()
            val initialText = initialSnapshot.record?.reflectionText.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    summary = initialSnapshot.summary,
                    savedText = initialText,
                    reflectionInput = initialText
                )
            }

            repository.observeDaySnapshot(date).collect { snapshot ->
                _uiState.update { state ->
                    state.copy(summary = snapshot.summary)
                }
            }
        }
    }

    fun updateReflectionInput(text: String) {
        _uiState.update { it.copy(reflectionInput = text) }
    }

    fun saveReflection() {
        val current = _uiState.value
        if (current.isSaving) return

        val textToSave = current.reflectionInput.trim()
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            repository.saveDailyReflection(date, textToSave)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    savedText = textToSave,
                    reflectionInput = textToSave,
                    shouldNavigateBack = true
                )
            }
        }
    }

    fun onBackRequested() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            _uiState.update { it.copy(shouldNavigateBack = true) }
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscardChanges() {
        _uiState.update {
            it.copy(
                showDiscardDialog = false,
                shouldNavigateBack = true
            )
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
                    if (modelClass.isAssignableFrom(DailyReflectionViewModel::class.java)) {
                        return DailyReflectionViewModel(repository, date) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}