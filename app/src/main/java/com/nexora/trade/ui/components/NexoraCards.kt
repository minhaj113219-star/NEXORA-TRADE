package com.nexora.trade.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyStatus
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import com.nexora.trade.ui.common.DateTimeFormatters
import com.nexora.trade.ui.theme.NexoraTheme
import java.math.BigDecimal

@Composable
fun NexoraCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = NexoraTheme.colors.cardBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun DailyStatusBadge(
    status: DailyStatus,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor) = when (status) {
        DailyStatus.NO_STARTING_BALANCE -> Triple(
            "SET BALANCE",
            NexoraTheme.colors.warningContainer,
            NexoraTheme.colors.onWarningContainer
        )
        DailyStatus.IN_PROGRESS -> Triple(
            "IN PROGRESS",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        DailyStatus.PROFIT_TARGET_COMPLETED -> Triple(
            "PROFIT TARGET COMPLETED",
            NexoraTheme.colors.profitContainer,
            NexoraTheme.colors.onProfitContainer
        )
        DailyStatus.LOSS_LIMIT_REACHED -> Triple(
            "LOSS LIMIT REACHED",
            NexoraTheme.colors.lossContainer,
            NexoraTheme.colors.onLossContainer
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusAlertBanner(
    status: DailyStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        DailyStatus.PROFIT_TARGET_COMPLETED -> {
            AlertBannerBox(
                modifier = modifier,
                icon = Icons.Rounded.CheckCircle,
                title = "DAILY PROFIT TARGET COMPLETED",
                message = "You reached your fixed +10% daily profit target. Protect your gains and stick to your plan.",
                containerColor = NexoraTheme.colors.profitContainer,
                accentColor = NexoraTheme.colors.profit,
                textColor = NexoraTheme.colors.onProfitContainer
            )
        }
        DailyStatus.LOSS_LIMIT_REACHED -> {
            AlertBannerBox(
                modifier = modifier,
                icon = Icons.Rounded.Warning,
                title = "DAILY LOSS LIMIT REACHED — STOP TRADING",
                message = "Your fixed −5% daily loss limit has been reached. Step away for today to protect your capital.",
                containerColor = NexoraTheme.colors.lossContainer,
                accentColor = NexoraTheme.colors.loss,
                textColor = NexoraTheme.colors.onLossContainer
            )
        }
        DailyStatus.NO_STARTING_BALANCE,
        DailyStatus.IN_PROGRESS -> Unit
    }
}

@Composable
private fun AlertBannerBox(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    message: String,
    containerColor: Color,
    accentColor: Color,
    textColor: Color
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun KpiMetricTile(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    NexoraCard(
        modifier = modifier,
        containerColor = NexoraTheme.colors.surfaceElevated
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTheme.colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TradeListRow(
    trade: TradeEntry,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isProfit = trade.type == TradeType.PROFIT
    val accentColor = if (isProfit) NexoraTheme.colors.profit else NexoraTheme.colors.loss
    val badgeBg = if (isProfit) NexoraTheme.colors.profitContainer else NexoraTheme.colors.lossContainer
    val signedAmount = MoneyCalculator.calculateSignedTradeAmount(trade)

    NexoraCard(
        modifier = modifier,
        containerColor = NexoraTheme.colors.surfaceElevated,
        onClick = onClick
    ) {
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
                    contentDescription = if (isProfit) "Profit" else "Loss",
                    tint = accentColor,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isProfit) "Profit Trade" else "Loss Trade",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trade.note.isNotBlank()) {
                        "${DateTimeFormatters.formatTimeFromMillis(trade.timestampMillis)} • ${trade.note}"
                    } else {
                        DateTimeFormatters.formatTimeFromMillis(trade.timestampMillis)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = MoneyCalculator.formatAmount(signedAmount, includePlusSign = true),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = accentColor
            )
        }
    }
}

@Composable
fun EmptyInfoCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    NexoraCard(
        modifier = modifier,
        containerColor = NexoraTheme.colors.surfaceElevated
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun pnlColorFor(amount: BigDecimal): Color {
    return when {
        amount > BigDecimal.ZERO -> NexoraTheme.colors.profit
        amount < BigDecimal.ZERO -> NexoraTheme.colors.loss
        else -> MaterialTheme.colorScheme.onSurface
    }
}