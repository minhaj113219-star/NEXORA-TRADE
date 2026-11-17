package com.nexora.trade.ui.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.components.DailyStatusBadge
import com.nexora.trade.ui.components.EmptyInfoCard
import com.nexora.trade.ui.components.KpiMetricTile
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.components.StatusAlertBanner
import com.nexora.trade.ui.components.TradeListRow
import com.nexora.trade.ui.components.pnlColorFor
import com.nexora.trade.ui.theme.NexoraTheme
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddTradeClick: (LocalDate) -> Unit,
    onOpenReflectionClick: (LocalDate) -> Unit,
    onOpenDailyDetailsClick: (LocalDate) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDateIfChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    StartingBalanceDialogHost(
        dialogState = uiState.dialogState,
        onAmountChange = viewModel::updateStartingBalanceInput,
        onSubmitInput = viewModel::submitStartingBalanceInput,
        onConfirmEdit = viewModel::confirmEditStartingBalance,
        onDismiss = viewModel::dismissStartingBalanceDialog
    )

    ThresholdAlertDialog(
        alertType = uiState.activeAlertBanner,
        onDismiss = viewModel::dismissAlertBanner
    )

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val summary = uiState.summary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DashboardHeader(
                todayDate = uiState.todayDate,
                status = summary.status
            )
        }

        if (summary.status == DailyStatus.PROFIT_TARGET_COMPLETED ||
            summary.status == DailyStatus.LOSS_LIMIT_REACHED
        ) {
            item {
                StatusAlertBanner(status = summary.status)
            }
        }

        item {
            HeroBalanceCard(
                summary = summary,
                onSetOrEditBalance = viewModel::openStartingBalanceDialog
            )
        }

        if (!summary.hasValidStartingBalance) {
            item {
                SetupStartingBalanceCallout(
                    onSetBalanceClick = viewModel::openStartingBalanceDialog
                )
            }
        } else {
            item {
                TargetsAndProgressSection(summary = summary)
            }
        }

        item {
            QuickActionsRow(
                hasValidStartingBalance = summary.hasValidStartingBalance,
                hasReflection = uiState.hasReflection,
                onAddTradeClick = { onAddTradeClick(uiState.todayDate) },
                onReflectionClick = { onOpenReflectionClick(uiState.todayDate) }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today's Trades (${summary.tradeCount})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(
                    onClick = { onOpenDailyDetailsClick(uiState.todayDate) }
                ) {
                    Text("Day Details")
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (uiState.todayTrades.isEmpty()) {
            item {
                EmptyInfoCard(
                    title = "No trades recorded today",
                    message = if (summary.hasValidStartingBalance) {
                        "Tap \"Add Trade\" to log a profit or loss result for today."
                    } else {
                        "Enter today's starting balance first, then log your trades manually."
                    }
                )
            }
        } else {
            items(
                items = uiState.todayTrades.take(5),
                key = { it.id }
            ) { trade ->
                TradeListRow(
                    trade = trade,
                    onClick = { onOpenDailyDetailsClick(uiState.todayDate) }
                )
            }

            if (uiState.todayTrades.size > 5) {
                item {
                    OutlinedButton(
                        onClick = { onOpenDailyDetailsClick(uiState.todayDate) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View All ${uiState.todayTrades.size} Trades")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DashboardHeader(
    todayDate: LocalDate,
    status: DailyStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NEXORA TRADE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateTimeFormatters.formatFullDate(todayDate),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        DailyStatusBadge(status = status)
    }
}

@Composable
private fun HeroBalanceCard(
    summary: DailyCalculationSummary,
    onSetOrEditBalance: () -> Unit
) {
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            NexoraTheme.colors.surfaceElevated,
            MaterialTheme.colorScheme.surface
        )
    )
    val netPnlColor = pnlColorFor(summary.netProfitLoss)
    val pnlContainerColor = when {
        summary.netProfitLoss > BigDecimal.ZERO -> NexoraTheme.colors.profitContainer
        summary.netProfitLoss < BigDecimal.ZERO -> NexoraTheme.colors.lossContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, NexoraTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .background(cardGradient)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Day Balance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onSetOrEditBalance,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (summary.hasValidStartingBalance) "Edit Start" else "Set Start"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (summary.hasValidStartingBalance) {
                    MoneyCalculator.formatAmount(summary.currentBalance)
                } else {
                    "—"
                },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Starting Balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTheme.colors.textMuted
                    )
                    Text(
                        text = if (summary.hasValidStartingBalance) {
                            MoneyCalculator.formatAmount(summary.startingBalance)
                        } else {
                            "Not set"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pnlContainerColor
                ) {
                    val pnlText = MoneyCalculator.formatAmount(
                        summary.netProfitLoss,
                        includePlusSign = true
                    )
                    val pctText = if (summary.hasValidStartingBalance) {
                        " (${MoneyCalculator.formatPercent(summary.profitPercentage, includePlusSign = true)})"
                    } else {
                        ""
                    }
                    Text(
                        text = "Net P/L: $pnlText$pctText",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = netPnlColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStartingBalanceCallout(
    onSetBalanceClick: () -> Unit
) {
    NexoraCard(
        containerColor = NexoraTheme.colors.surfaceElevated,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    ) {
        Text(
            text = "Start Today's Trading Plan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your starting account balance for today to lock in your fixed +10% profit target and −5% maximum daily loss limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onSetBalanceClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Today's Starting Balance")
        }
    }
}

@Composable
private fun TargetsAndProgressSection(
    summary: DailyCalculationSummary
) {
    val animatedProgress by animateFloatAsState(
        targetValue = summary.targetProgressFraction,
        animationSpec = tween(durationMillis = 450),
        label = "targetProgress"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NexoraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress to +10% Daily Target",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = MoneyCalculator.formatPercent(summary.targetProgressPercent),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = NexoraTheme.colors.profit
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = NexoraTheme.colors.profit,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = NexoraTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Remaining to Target",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTheme.colors.textMuted
                    )
                    Text(
                        text = MoneyCalculator.formatAmount(summary.remainingToTarget),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Remaining Loss Allowance",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTheme.colors.textMuted
                    )
                    Text(
                        text = MoneyCalculator.formatAmount(summary.remainingLossAllowance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (summary.remainingLossAllowance.compareTo(BigDecimal.ZERO) == 0) {
                            NexoraTheme.colors.loss
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiMetricTile(
                title = "Profit Target (+10%)",
                value = "+${MoneyCalculator.formatAmount(summary.dailyProfitTarget)}",
                subtitle = "Goal Bal: ${MoneyCalculator.formatAmount(summary.targetBalance)}",
                accentColor = NexoraTheme.colors.profit,
                icon = Icons.Rounded.Flag,
                modifier = Modifier.weight(1f)
            )

            KpiMetricTile(
                title = "Max Loss (−5%)",
                value = "−${MoneyCalculator.formatAmount(summary.maxDailyLoss)}",
                subtitle = "Stop Bal: ${MoneyCalculator.formatAmount(summary.stopTradingBalance)}",
                accentColor = NexoraTheme.colors.loss,
                icon = Icons.Rounded.Shield,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    hasValidStartingBalance: Boolean,
    hasReflection: Boolean,
    onAddTradeClick: () -> Unit,
    onReflectionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAddTradeClick,
            enabled = hasValidStartingBalance,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Trade")
        }

        OutlinedButton(
            onClick = onReflectionClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (hasReflection) "Edit Note" else "Reflection")
        }
    }
}