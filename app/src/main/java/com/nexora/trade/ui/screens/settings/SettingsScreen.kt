package com.nexora.trade.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.trade.R
import com.nexora.trade.ui.components.NexoraCard
import com.nexora.trade.ui.theme.NexoraTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshNotificationStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings & Rules",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Preferences, alerts, and fixed money-management formulas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Version ${uiState.appVersion}  •  100% Offline Local Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTheme.colors.textMuted
                )
            }
        }

        item {
            NexoraCard {
                SectionHeader(
                    icon = Icons.Rounded.DarkMode,
                    title = "Appearance & Alerts"
                )
                Spacer(modifier = Modifier.height(10.dp))

                ToggleRow(
                    title = "Dark Theme",
                    subtitle = "Recommended high-contrast fintech dark appearance",
                    checked = uiState.isDarkTheme,
                    onCheckedChange = viewModel::toggleDarkTheme
                )

                HorizontalDivider(
                    color = NexoraTheme.colors.cardBorder,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ToggleRow(
                    title = "Alert Sound",
                    subtitle = "Play notification tone when +10% or −5% threshold is reached",
                    checked = uiState.isSoundEnabled,
                    onCheckedChange = viewModel::toggleSound
                )

                HorizontalDivider(
                    color = NexoraTheme.colors.cardBorder,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ToggleRow(
                    title = "Alert Vibration",
                    subtitle = "Vibrate on threshold alerts when device settings allow",
                    checked = uiState.isVibrationEnabled,
                    onCheckedChange = viewModel::toggleVibration
                )

                HorizontalDivider(
                    color = NexoraTheme.colors.cardBorder,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                NotificationPermissionRow(
                    hasPermission = uiState.hasNotificationPermission,
                    systemEnabled = uiState.areSystemNotificationsEnabled,
                    onRequestOrOpenSettings = {
                        if (!uiState.hasNotificationPermission &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                )
            }
        }

        item {
            NexoraCard {
                SectionHeader(
                    icon = Icons.Rounded.Calculate,
                    title = "Fixed Calculation Rules"
                )
                Spacer(modifier = Modifier.height(12.dp))

                RuleBlock(
                    title = "1. Daily Starting Balance",
                    body = "Manually entered at the start of each calendar day. It stays fixed for that day and never changes automatically from trades. Editing it requires confirmation.",
                    accentColor = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                RuleBlock(
                    title = "2. Fixed Daily Profit Target (+10%)",
                    body = "Daily Profit Target = Starting Balance × 10%\n" +
                        "Target Balance = Starting Balance + Daily Profit Target\n" +
                        "Example: 100 → +10.00 (Target 110.00)  |  250 → +25.00 (Target 275.00)",
                    accentColor = NexoraTheme.colors.profit
                )

                Spacer(modifier = Modifier.height(12.dp))

                RuleBlock(
                    title = "3. Fixed Daily Loss Limit (−5%)",
                    body = "Maximum Daily Loss = Starting Balance × 5%\n" +
                        "Stop-Trading Balance = Starting Balance − Maximum Daily Loss\n" +
                        "Example: 100 → −5.00 (Stop 95.00)  |  250 → −12.50 (Stop 237.50)",
                    accentColor = NexoraTheme.colors.loss
                )

                Spacer(modifier = Modifier.height(12.dp))

                RuleBlock(
                    title = "4. Daily Net P/L & Balance",
                    body = "Daily Net P/L = Sum of all recorded trades for that date\n" +
                        "Current Day Balance = Starting Balance + Daily Net P/L\n" +
                        "Profit % = (Daily Net P/L ÷ Starting Balance) × 100",
                    accentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            NexoraCard(containerColor = NexoraTheme.colors.surfaceElevated) {
                SectionHeader(
                    icon = Icons.Rounded.Shield,
                    title = "Data Privacy & Scope"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = NexoraTheme.colors.textMuted,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXORA TRADE is a manual offline money-management journal. All records are stored locally on your device. This app does not connect to any broker, place trades, provide trading signals, or guarantee trading profits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun NotificationPermissionRow(
    hasPermission: Boolean,
    systemEnabled: Boolean,
    onRequestOrOpenSettings: () -> Unit
) {
    val active = hasPermission && systemEnabled
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = if (active) NexoraTheme.colors.profit else NexoraTheme.colors.warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "System Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (active) {
                        "Enabled for threshold alerts"
                    } else {
                        "Disabled — in-app alerts still work"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) NexoraTheme.colors.profit else NexoraTheme.colors.warning
                )
            }
        }

        OutlinedButton(onClick = onRequestOrOpenSettings) {
            Text(if (active) "Settings" else "Enable")
        }
    }
}

@Composable
private fun RuleBlock(
    title: String,
    body: String,
    accentColor: Color
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    val launched = runCatching {
        context.startActivity(notificationIntent)
    }.isSuccess

    if (!launched) {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        runCatching { context.startActivity(fallbackIntent) }
    }
}
