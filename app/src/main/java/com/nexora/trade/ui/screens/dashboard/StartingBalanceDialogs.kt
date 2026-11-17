package com.nexora.trade.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.model.DailyAlertType
import com.nexora.trade.ui.theme.NexoraTheme

@Composable
fun StartingBalanceDialogHost(
    dialogState: StartingBalanceDialogState,
    onAmountChange: (String) -> Unit,
    onSubmitInput: () -> Unit,
    onConfirmEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    when (dialogState) {
        StartingBalanceDialogState.Hidden -> Unit

        is StartingBalanceDialogState.Input -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = if (dialogState.isEditingExisting) {
                            "Edit Starting Balance"
                        } else {
                            "Set Today's Starting Balance"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "This value stays fixed for today and determines your +10% profit target and −5% loss limit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = dialogState.rawAmount,
                            onValueChange = onAmountChange,
                            label = { Text("Starting Balance") },
                            placeholder = { Text("e.g., 100.00") },
                            singleLine = true,
                            isError = dialogState.errorMessage != null,
                            supportingText = dialogState.errorMessage?.let { msg ->
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
                },
                confirmButton = {
                    Button(onClick = onSubmitInput) {
                        Text(if (dialogState.isEditingExisting) "Continue" else "Save Balance")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }

        is StartingBalanceDialogState.ConfirmEdit -> {
            val formattedNew = MoneyCalculator.formatAmount(dialogState.proposedBalance)
            val newTarget = MoneyCalculator.formatAmount(
                MoneyCalculator.calculateDailyProfitTarget(dialogState.proposedBalance)
            )
            val newLoss = MoneyCalculator.formatAmount(
                MoneyCalculator.calculateMaxDailyLoss(dialogState.proposedBalance)
            )

            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Confirm Starting Balance Change",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Updating today's starting balance to $formattedNew will recalculate today's fixed thresholds:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Daily Profit Target (+10%): +$newTarget\n• Maximum Daily Loss (−5%): −$newLoss",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your recorded trades for today will remain intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexoraTheme.colors.textMuted
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = onConfirmEdit) {
                        Text("Confirm Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ThresholdAlertDialog(
    alertType: DailyAlertType?,
    onDismiss: () -> Unit
) {
    if (alertType == null) return

    val isProfit = alertType == DailyAlertType.PROFIT_TARGET_COMPLETED
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (isProfit) {
                    "DAILY PROFIT TARGET COMPLETED"
                } else {
                    "DAILY LOSS LIMIT REACHED — STOP TRADING"
                },
                color = if (isProfit) NexoraTheme.colors.profit else NexoraTheme.colors.loss,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = if (isProfit) {
                    "You have reached your fixed +10% profit goal for today."
                } else {
                    "You have reached your fixed −5% maximum loss limit for today. Stop trading to protect your account."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}