package com.nexora.trade.ui.screens.thirtyday

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.ThirtyDayDayItem
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.components.DailyStatusBadge
import com.nexora.trade.ui.components.EmptyInfoCard
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.components.pnlColorFor
import com.nexora.trade.ui.theme.NexoraTheme
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun ThirtyDayManagementScreen(
    viewModel: ThirtyDayViewModel,
    onDayClick: (LocalDate) -> Unit
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "30-Day Management",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${DateTimeFormatters.formatMediumDate(summary.startDate)} – ${
                        DateTimeFormatters.formatMediumDate(summary.endDate)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ThirtyDayOverviewCard(summary = summary)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThirtyDayFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (uiState.filteredDays.isEmpty()) {
            item {
                EmptyInfoCard(
                    title = "No matching days",
                    message = "No saved trading records match the selected filter inside the last 30 calendar days."
                )
            }
        } else {
            items(
                items = uiState.filteredDays,
                key = { it.date.toString() }
            ) { dayItem ->
                ThirtyDayRowCard(
                    item = dayItem,
                    onClick = { onDayClick(dayItem.date) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThirtyDayOverviewCard(summary: ThirtyDaySummary) {
    NexoraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "30-Day Net Profit / Loss",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = MoneyCalculator.formatAmount(
                        summary.totalNetProfitLoss,
                        includePlusSign = true
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = pnlColorFor(summary.totalNetProfitLoss)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Recorded Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${summary.recordedDaysCount} / 30",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = NexoraTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OverviewStatColumn(
                label = "Total Trades",
                value = summary.totalTradesCount.toString(),
                valueColor = MaterialTheme.colorScheme.onSurface
            )
            OverviewStatColumn(
                label = "Win / Loss / Even",
                value = "${summary.winningDaysCount} / ${summary.losingDaysCount} / ${summary.breakEvenDaysCount}",
                valueColor = MaterialTheme.colorScheme.onSurface
            )
            OverviewStatColumn(
                label = "+10% / −5%",
                value = "${summary.targetCompletedDaysCount} / ${summary.lossLimitReachedDaysCount}",
                valueColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OverviewStatColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTheme.colors.textMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}

@Composable
private fun ThirtyDayRowCard(
    item: ThirtyDayDayItem,
    onClick: () -> Unit
) {
    if (!item.hasAnyRecord) {
        NexoraCard(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DateTimeFormatters.formatShortDayDate(item.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No record",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTheme.colors.textMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "NO RECORD",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoraTheme.colors.textMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        return
    }

    val summary = item.summary
    val isBreakEvenZero = summary.netProfitLoss.compareTo(BigDecimal.ZERO) == 0

    NexoraCard(
        containerColor = NexoraTheme.colors.surfaceElevated,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = DateTimeFormatters.formatShortDayDate(item.date),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.hasReflection) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = "Has daily reflection",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            DailyStatusBadge(status = summary.status)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (item.hasStartingBalance) {
                        "Start: ${MoneyCalculator.formatAmount(summary.startingBalance)}"
                    } else {
                        "Start: Not set"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        item.hasTrades -> "${summary.tradeCount} trade(s)"
                        isBreakEvenZero -> "0 trades • Break-even"
                        else -> "0 trades"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTheme.colors.textMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyCalculator.formatAmount(
                            summary.netProfitLoss,
                            includePlusSign = true
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = pnlColorFor(summary.netProfitLoss)
                    )
                    if (summary.hasValidStartingBalance) {
                        Text(
                            text = MoneyCalculator.formatPercent(
                                summary.profitPercentage,
                                includePlusSign = true
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = pnlColorFor(summary.netProfitLoss)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open day details",
                    tint = NexoraTheme.colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}