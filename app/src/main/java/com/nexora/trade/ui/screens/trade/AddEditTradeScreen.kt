package com.nexora.trade.ui.screens.trade

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.components.EmptyInfoCard
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.components.pnlColorFor
import com.nexora.trade.ui.screens.dashboard.ThresholdAlertDialog
import com.nexora.trade.ui.theme.NexoraTheme

private val PRESET_PAYOUTS = listOf("80", "85", "90", "95", "100")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTradeScreen(
    viewModel: AddEditTradeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.shouldNavigateBack) {
        if (uiState.shouldNavigateBack) {
            onNavigateBack()
        }
    }

    val confirmAmount = uiState.pendingConfirmAmount
    if (confirmAmount != null) {
        val signedPreview = if (uiState.selectedType == TradeType.PROFIT) {
            confirmAmount
        } else {
            confirmAmount.negate()
        }
        val formattedSigned = MoneyCalculator.formatAmount(signedPreview, includePlusSign = true)
        val confirmStake = uiState.pendingConfirmStake
        val confirmPayout = uiState.pendingConfirmPayout

        AlertDialog(
            onDismissRequest = viewModel::dismissSaveConfirmation,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (uiState.isEditMode) "Confirm Trade Update" else "Confirm Trade Entry",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "Date: ${DateTimeFormatters.formatMediumDate(uiState.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (confirmStake != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val payoutInfo = if (uiState.selectedType == TradeType.PROFIT && confirmPayout != null) {
                            "  •  Payout: ${MoneyCalculator.formatPayoutPercent(confirmPayout)}"
                        } else {
                            ""
                        }
                        Text(
                            text = "Trade Amount: ${MoneyCalculator.formatAmount(confirmStake)}$payoutInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.selectedType == TradeType.PROFIT) {
                            "Net Result: Profit ($formattedSigned)"
                        } else {
                            "Net Result: Loss ($formattedSigned)"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.selectedType == TradeType.PROFIT) {
                            NexoraTheme.colors.profit
                        } else {
                            NexoraTheme.colors.loss
                        }
                    )
                    if (uiState.noteInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: ${uiState.noteInput.trim()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmSaveTrade) {
                    Text(if (uiState.isEditMode) "Update Trade" else "Save Trade")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }

    ThresholdAlertDialog(
        alertType = uiState.triggeredAlert,
        onDismiss = viewModel::dismissAlertAndFinish
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = if (uiState.isEditMode) "Edit Trade Result" else "Add Trade Result")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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

        if (uiState.tradeNotFound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyInfoCard(
                    title = "Trade record not found",
                    message = "This trade entry may have already been deleted."
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
                Text(
                    text = DateTimeFormatters.formatFullDate(uiState.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uiState.daySummary.hasValidStartingBalance) {
                            "Start: ${MoneyCalculator.formatAmount(uiState.daySummary.startingBalance)}"
                        } else {
                            "Starting balance not set"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Net P/L: ${MoneyCalculator.formatAmount(uiState.daySummary.netProfitLoss, includePlusSign = true)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = pnlColorFor(uiState.daySummary.netProfitLoss)
                    )
                }
            }

            if (!uiState.isEditMode && !uiState.daySummary.hasValidStartingBalance) {
                EmptyInfoCard(
                    title = "Starting balance required",
                    message = "Go back to the Dashboard and set the day's starting balance before recording new trades."
                )
            } else {
                Text(
                    text = "Trade Outcome",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TradeTypeCard(
                        title = "Profit (+)",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        selected = uiState.selectedType == TradeType.PROFIT,
                        accentColor = NexoraTheme.colors.profit,
                        containerColor = NexoraTheme.colors.profitContainer,
                        onClick = { viewModel.selectTradeType(TradeType.PROFIT) },
                        modifier = Modifier.weight(1f)
                    )

                    TradeTypeCard(
                        title = "Loss (−)",
                        icon = Icons.AutoMirrored.Rounded.TrendingDown,
                        selected = uiState.selectedType == TradeType.LOSS,
                        accentColor = NexoraTheme.colors.loss,
                        containerColor = NexoraTheme.colors.lossContainer,
                        onClick = { viewModel.selectTradeType(TradeType.LOSS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = uiState.amountInput,
                    onValueChange = viewModel::updateAmountInput,
                    label = { Text("Trade Amount") },
                    placeholder = { Text("e.g., 10.00") },
                    singleLine = true,
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.selectedType == TradeType.PROFIT) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Asset Payout (%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PRESET_PAYOUTS.forEach { preset ->
                                val isSelected = uiState.payoutInput.trim() == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updatePayoutInput(preset) },
                                    label = { Text("$preset%") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NexoraTheme.colors.profitContainer,
                                        selectedLabelColor = NexoraTheme.colors.profit
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.payoutInput,
                            onValueChange = viewModel::updatePayoutInput,
                            label = { Text("Payout Percentage (%)") },
                            placeholder = { Text("e.g., 85") },
                            suffix = { Text("%") },
                            singleLine = true,
                            isError = uiState.payoutError != null,
                            supportingText = uiState.payoutError?.let { msg ->
                                {
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val previewNet = uiState.previewNetAmount
                if (previewNet != null) {
                    val isProfit = uiState.selectedType == TradeType.PROFIT
                    val accentColor = if (isProfit) NexoraTheme.colors.profit else NexoraTheme.colors.loss
                    val bgColor = if (isProfit) NexoraTheme.colors.profitContainer else NexoraTheme.colors.lossContainer
                    val signedNet = if (isProfit) previewNet else previewNet.negate()

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = bgColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isProfit) {
                                    "Net Return (${uiState.payoutInput.trim()}%)"
                                } else {
                                    "Net Loss (100% of stake)"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = accentColor
                            )
                            Text(
                                text = MoneyCalculator.formatAmount(signedNet, includePlusSign = true),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = accentColor
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.noteInput,
                    onValueChange = viewModel::updateNoteInput,
                    label = { Text("Optional Note") },
                    placeholder = { Text("Setup, pair, or session note (max 120 chars)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = viewModel::requestSaveConfirmation,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (uiState.isEditMode) "Review & Update Trade" else "Review & Save Trade",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeTypeCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) containerColor else NexoraTheme.colors.surfaceElevated,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else NexoraTheme.colors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}