                val payoutSuffix = if (isProfit && trade.stakeAmount != null && trade.payoutPercent != null) {
                    " • ${MoneyCalculator.formatAmount(trade.stakeAmount)} @ ${MoneyCalculator.formatPayoutPercent(trade.payoutPercent)}"
                } else {
                    ""
                }
                val noteSuffix = if (trade.note.isNotBlank()) " • ${trade.note}" else ""
                Text(
                    text = "${DateTimeFormatters.formatTimeFromMillis(trade.timestampMillis)}$payoutSuffix$noteSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )