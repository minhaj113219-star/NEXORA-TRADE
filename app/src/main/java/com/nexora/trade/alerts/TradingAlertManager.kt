package com.nexora.trade.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexora.trade.MainActivity
import com.nexora.trade.R
import com.nexora.trade.domain.model.DailyAlertType
import java.time.LocalDate

interface NotificationStatusProvider {
    fun hasNotificationPermission(): Boolean
    fun areSystemNotificationsEnabled(): Boolean
}

interface TradingAlertManager : NotificationStatusProvider {
    fun dispatchThresholdAlert(alertType: DailyAlertType, date: LocalDate)
}

object NoOpTradingAlertManager : TradingAlertManager {
    override fun dispatchThresholdAlert(alertType: DailyAlertType, date: LocalDate) = Unit
    override fun hasNotificationPermission(): Boolean = true
    override fun areSystemNotificationsEnabled(): Boolean = true
}

class AndroidTradingAlertManager(
    context: Context,
    private val alertPreferences: AppSettingsStore
) : TradingAlertManager {

    private val appContext = context.applicationContext

    init {
        ensureNotificationChannel()
    }

    override fun dispatchThresholdAlert(alertType: DailyAlertType, date: LocalDate) {
        if (alertPreferences.isSoundEnabled()) {
            playAlertSoundSafely()
        }
        if (alertPreferences.isVibrationEnabled()) {
            triggerVibrationSafely(alertType)
        }
        postNotificationIfPermitted(alertType, date)
    }

    override fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun areSystemNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun ensureNotificationChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trading Threshold Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when your fixed +10% profit target or −5% loss limit is reached."
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun postNotificationIfPermitted(alertType: DailyAlertType, date: LocalDate) {
        if (!hasNotificationPermission() || !areSystemNotificationsEnabled()) {
            return
        }

        val (title, body) = when (alertType) {
            DailyAlertType.PROFIT_TARGET_COMPLETED -> Pair(
                "DAILY PROFIT TARGET COMPLETED",
                "You reached your fixed +10% profit target for $date."
            )
            DailyAlertType.LOSS_LIMIT_REACHED -> Pair(
                "DAILY LOSS LIMIT REACHED — STOP TRADING",
                "You reached your fixed −5% daily loss limit for $date. Stop trading for today."
            )
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            alertType.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_trade)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        val notificationId = (date.toString().hashCode() * 31) + alertType.ordinal
        runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        }
    }

    private fun playAlertSoundSafely() {
        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(appContext, uri) ?: return
            ringtone.play()
        }
    }

    private fun triggerVibrationSafely(alertType: DailyAlertType) {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                return
            }

            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return

            val effect = when (alertType) {
                DailyAlertType.PROFIT_TARGET_COMPLETED -> {
                    VibrationEffect.createOneShot(320L, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                DailyAlertType.LOSS_LIMIT_REACHED -> {
                    VibrationEffect.createWaveform(longArrayOf(0L, 220L, 110L, 260L), -1)
                }
            }
            vibrator.vibrate(effect)
        }
    }

    companion object {
        const val CHANNEL_ID = "nexora_trade_alerts_v1"
    }
}