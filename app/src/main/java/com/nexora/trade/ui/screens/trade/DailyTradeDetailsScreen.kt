package com.nexora.trade.ui.screens.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.components.DailyStatusBadge
import com.nexora.trade.ui.components.EmptyInfoCard
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.components.StatusAlertBanner
import com.nexora.trade.ui.components.pnlColorFor
import com.nexora.trade.ui.theme.NexoraTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTradeDetailsScreen(
    viewModel: DailyTradeDetailsViewModel,
    onNavigateBack: () -> Unit,
    onAddTradeClick: (LocalDate) -> Unit,
    onEditTradeClick: (LocalDate, Long) -> Unit,
    onOpenReflectionClick: (LocalDate) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tradeToDelete = uiState.tradePendingDelete
    if (tradeToDelete != null) {
        val signedAmount = MoneyCalculator.formatAmount(
            MoneyCalculator.calculateSignedTradeAmount(tradeToDelete),
            includePlusSign = true
        )
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Delete Trade Entry?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Delete this ${if (tradeToDelete.type == TradeType.PROFIT) "Profit" else "Loss"} entry ($signedAmount)? Your daily net P/L and current balance will be recalculated automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteTrade,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraTheme.colors.loss
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("Cancel")
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
                            text = "Daily Trade Details",
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
        },
        floatingActionButton = {
            if (uiState.summary.hasValidStartingBalance) {
                FloatingActionButton(
                    onClick = { onAddTradeClick(uiState.date) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Trade"
                    )
                }
            }
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (summary.status == DailyStatus.PROFIT_TARGET_COMPLETED ||
                summary.status == DailyStatus.LOSS_LIMIT_REACHED
            ) {
                item {
                    StatusAlertBanner(status = summary.status)
                }
            }

            item {
                DayBreakdownCard(summary = summary)
            }

            item {
                ReflectionPreviewCard(
                    reflectionText = uiState.reflectionText,
                    onOpenReflection = { onOpenReflectionClick(uiState.date) }
                )
            }

            item {
                Text(
                    text = "Recorded Trades (${uiState.trades.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (uiState.trades.isEmpty()) {
                item {
                    EmptyInfoCard(
                        title = "No trades recorded for this day",
                        message = if (summary.hasValidStartingBalance) {
                            "Tap the + button to add a trade result for this day."
                        } else {
                            "No starting balance was recorded for this day."
                        }
                    )
                }
            } else {
                items(
                    items = uiState.trades,
                    key = { it.id }
                ) { trade ->
                    EditableTradeRowCard(
                        trade = trade,
                        onEditClick = { onEditTradeClick(uiState.date, trade.id) },
                        onDeleteClick = { viewModel.requestDeleteTrade(trade) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun DayBreakdownCard(summary: DailyCalculationSummary) {
    NexoraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateTimeFormatters.formatFullDate(summary.date),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            DailyStatusBadge(status = summary.status)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!summary.hasValidStartingBalance) {
            Text(
                text = "Starting Balance: Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SummaryMetricRow(
                label = "Starting Balance",
                value = MoneyCalculator.formatAmount(summary.startingBalance)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryMetricRow(
                label = "Current Balance",
                value = MoneyCalculator.formatAmount(summary.currentBalance)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryMetricRow(
                label = "Net Profit / Loss",
                value = "${MoneyCalculator.formatAmount(summary.netProfitLoss, includePlusSign = true)} (${
                    MoneyCalculator.formatPercent(summary.profitPercentage, includePlusSign = true)
                })",
                valueColor = pnlColorFor(summary.netProfitLoss)
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = NexoraTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))
            SummaryMetricRow(
                label = "Fixed Profit Target (+10%)",
                value = "+${MoneyCalculator.formatAmount(summary.dailyProfitTarget)} (Goal ${
                    MoneyCalculator.formatAmount(summary.targetBalance)
                })",
                valueColor = NexoraTheme.colors.profit
            )
            Spacer(modifier = Modifier.height(6.dp))
            SummaryMetricRow(
                label = "Fixed Loss Limit (−5%)",
                value = "−${MoneyCalculator.formatAmount(summary.maxDailyLoss)} (Stop ${
                    MoneyCalculator.formatAmount(summary.stopTradingBalance)
                })",
                valueColor = NexoraTheme.colors.loss
            )
        }
    }
}

@Composable
private fun SummaryMetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}

@Composable
private fun ReflectionPreviewCard(
    reflectionText: String,
    onOpenReflection: () -> Unit
) {
    NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Daily Reflection",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedButton(onClick = onOpenReflection) {
                Text(if (reflectionText.isBlank()) "Write Note" else "Edit Note")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (reflectionText.isBlank()) {
                "No daily reflection saved for this date."
            } else {
                reflectionText
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditableTradeRowCard(
    trade: TradeEntry,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isProfit = trade.type == TradeType.PROFIT
    val accentColor = if (isProfit) NexoraTheme.colors.profit else NexoraTheme.colors.loss
    val badgeBg = if (isProfit) NexoraTheme.colors.profitContainer else NexoraTheme.colors.lossContainer
    val signedAmount = MoneyCalculator.calculateSignedTradeAmount(trade)

    NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isProfit) {
                        Icons.AutoMirrored.Rounded.TrendingUp
                    } else {
                        Icons.AutoMirrored.Rounded.TrendingDown
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = MoneyCalculator.formatAmount(signedAmount, includePlusSign = true),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = accentColor
                )
                Text(
                    text = DateTimeFormatters.formatTimeFromMillis(trade.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTheme.colors.textMuted
                )
                if (trade.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trade.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Trade",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete Trade",
                    tint = NexoraTheme.colors.loss
                )
            }
        }
    }
}