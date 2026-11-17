package com.nexora.trade.ui.screens.reflection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.components.DailyStatusBadge
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.components.pnlColorFor
import com.nexora.trade.ui.theme.NexoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReflectionScreen(
    viewModel: DailyReflectionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.shouldNavigateBack) {
        if (uiState.shouldNavigateBack) {
            onNavigateBack()
        }
    }

    BackHandler(enabled = uiState.hasUnsavedChanges) {
        viewModel.onBackRequested()
    }

    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Discard Unsaved Reflection?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes to your reflection for ${
                        DateTimeFormatters.formatMediumDate(uiState.date)
                    }. Leave without saving?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDiscardChanges,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraTheme.colors.loss
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardDialog) {
                    Text("Keep Editing")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Daily Reflection",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = DateTimeFormatters.formatMediumDate(uiState.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackRequested) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val summary = uiState.summary

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateTimeFormatters.formatFullDate(uiState.date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (summary.hasValidStartingBalance) {
                                "Net P/L: ${
                                    MoneyCalculator.formatAmount(
                                        summary.netProfitLoss,
                                        includePlusSign = true
                                    )
                                } (${summary.tradeCount} trade(s))"
                            } else {
                                "No starting balance recorded for this date"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (summary.hasValidStartingBalance) {
                                pnlColorFor(summary.netProfitLoss)
                            } else {
                                NexoraTheme.colors.textMuted
                            }
                        )
                    }
                    DailyStatusBadge(status = summary.status)
                }
            }

            Text(
                text = "How did your trading session go today?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = uiState.reflectionInput,
                onValueChange = viewModel::updateReflectionInput,
                label = { Text("Trading Day Reflection") },
                placeholder = {
                    Text("Write what worked well, emotional discipline, mistakes to avoid, or lessons learned...")
                },
                minLines = 7,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (uiState.hasUnsavedChanges) "Unsaved changes" else "Saved locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.hasUnsavedChanges) {
                        NexoraTheme.colors.warning
                    } else {
                        NexoraTheme.colors.textMuted
                    }
                )
                Text(
                    text = "${uiState.reflectionInput.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTheme.colors.textMuted
                )
            }

            Button(
                onClick = viewModel::saveReflection,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (uiState.savedText.isBlank()) {
                        "Save Reflection"
                    } else {
                        "Update Reflection"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}