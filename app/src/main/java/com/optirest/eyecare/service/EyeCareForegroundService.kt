package com.optirest.eyecare.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.optirest.eyecare.MainActivity
import com.optirest.eyecare.R
import com.optirest.eyecare.data.AppConfig
import com.optirest.eyecare.data.DailyUsageRecord
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.receiver.NotificationActionReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EyeCareUiState(
    val isServiceRunning: Boolean = false,
    val isScreenOnAndUnlocked: Boolean = true,
    val isManualPaused: Boolean = false,
    val isOnBreak: Boolean = false,
    val breakDueModalOpen: Boolean = false,
    val breakRemainingSec: Int = 20,
    val cycleElapsedMs: Long = 0L,
    val todayRecord: DailyUsageRecord? = null,
    val last7Days: List<DailyUsageRecord> = emptyList(),
    val config: AppConfig = AppConfig()
)

object ServiceStateHolder {
    private val _uiState = MutableStateFlow(EyeCareUiState())
    val uiState: StateFlow<EyeCareUiState> = _uiState.asStateFlow()

    fun update(transform: (EyeCareUiState) -> EyeCareUiState) {
        _uiState.value = transform(_uiState.value)
    }
}

class EyeCareForegroundService : Service() {

    companion object {
        const val ACTION_START_OR_SYNC = "com.optirest.eyecare.ACTION_START_OR_SYNC"
        const val ACTION_TOGGLE_PAUSE = "com.optirest.eyecare.ACTION_TOGGLE_PAUSE"
        const val ACTION_START_BREAK = "com.optirest.eyecare.ACTION_START_BREAK"
        const val ACTION_COMPLETE_BREAK = "com.optirest.eyecare.ACTION_COMPLETE_BREAK"
        const val ACTION_SNOOZE_BREAK = "com.optirest.eyecare.ACTION_SNOOZE_BREAK"
        const val ACTION_SKIP_BREAK = "com.optirest.eyecare.ACTION_SKIP_BREAK"
        const val ACTION_RESET_CYCLE = "com.optirest.eyecare.ACTION_RESET_CYCLE"
        const val ACTION_SYNC_CONFIG = "com.optirest.eyecare.ACTION_SYNC_CONFIG"

        private const val MONITOR_CHANNEL_ID = "optirest_monitor_channel"
        private const val BREAK_CHANNEL_ID = "optirest_break_alert_channel"
        private const val MONITOR_NOTIF_ID = 1001
        private const val BREAK_NOTIF_ID = 1002
    }

    private lateinit var repository: UsageRepository
    private lateinit var overlayManager: BlueLightOverlayManager
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var config: AppConfig = AppConfig()
    private var cycleElapsedMs: Long = 0L
    private var lastTickElapsedRealtime: Long = 0L
    private var isScreenInteractive: Boolean = true
    private var isDeviceUnlocked: Boolean = true
    private var isManualPaused: Boolean = false
    private var isOnBreak: Boolean = false
    private var breakRemainingSec: Int = 20
    private var breakAlertDispatched: Boolean = false
    private var snoozeUntilElapsedRealtime: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Immediately flush any active time up to screen-off moment and stop ticking
                    flushActiveDelta()
                    isScreenInteractive = false
                    isDeviceUnlocked = false
                    stopTickLoop()
                    overlayManager.removeOverlay()
                    emitState()
                    updateForegroundNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    isDeviceUnlocked = km?.isKeyguardLocked == false
                    lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                    if (isDeviceUnlocked) {
                        overlayManager.applyFilter(
                            config.filterEnabled,
                            config.filterWarmthKelvin,
                            config.filterAlphaPercent
                        )
                        startTickLoopIfActive()
                    }
                    emitState()
                    updateForegroundNotification()
                }

                Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    isDeviceUnlocked = true
                    lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                    overlayManager.applyFilter(
                        config.filterEnabled,
                        config.filterWarmthKelvin,
                        config.filterAlphaPercent
                    )
                    startTickLoopIfActive()
                    emitState()
                    updateForegroundNotification()
                }
            }
        }
    }

    private val activeTickRunnable = object : Runnable {
        override fun run() {
            if (isCurrentlyCounting()) {
                flushActiveDelta()
                checkBreakTrigger()
                emitState()
                updateForegroundNotification()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val breakCountdownRunnable = object : Runnable {
        override fun run() {
            if (!isOnBreak) return
            if (breakRemainingSec > 1) {
                breakRemainingSec -= 1
                emitState()
                handler.postDelayed(this, 1000L)
            } else {
                completeBreak()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = UsageRepository(this)
        overlayManager = BlueLightOverlayManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()

        config = repository.loadConfig()
        cycleElapsedMs = repository.loadCycleElapsedMs()

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isScreenInteractive = pm?.isInteractive ?: true
        isDeviceUnlocked = km?.isKeyguardLocked == false
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MONITOR_NOTIF_ID,
                buildMonitorNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(MONITOR_NOTIF_ID, buildMonitorNotification())
        }

        if (isScreenInteractive && isDeviceUnlocked) {
            overlayManager.applyFilter(
                config.filterEnabled,
                config.filterWarmthKelvin,
                config.filterAlphaPercent
            )
        }

        startTickLoopIfActive()
        emitState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_CONFIG -> {
                config = repository.loadConfig()
                if (isScreenInteractive && isDeviceUnlocked) {
                    overlayManager.applyFilter(
                        config.filterEnabled,
                        config.filterWarmthKelvin,
                        config.filterAlphaPercent
                    )
                }
                emitState()
                updateForegroundNotification()
            }

            ACTION_TOGGLE_PAUSE -> {
                if (!isManualPaused) {
                    flushActiveDelta()
                }
                isManualPaused = !isManualPaused
                lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                if (isManualPaused) stopTickLoop() else startTickLoopIfActive()
                emitState()
                updateForegroundNotification()
            }

            ACTION_START_BREAK -> startBreak()
            ACTION_COMPLETE_BREAK -> completeBreak()
            ACTION_SNOOZE_BREAK -> snoozeBreak()
            ACTION_SKIP_BREAK -> skipBreak()
            ACTION_RESET_CYCLE -> {
                cycleElapsedMs = 0L
                breakAlertDispatched = false
                snoozeUntilElapsedRealtime = 0L
                repository.saveCycleElapsedMs(0L)
                lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                emitState()
                updateForegroundNotification()
            }

            else -> {
                config = repository.loadConfig()
                startTickLoopIfActive()
                emitState()
                updateForegroundNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        flushActiveDelta()
        stopTickLoop()
        handler.removeCallbacks(breakCountdownRunnable)
        overlayManager.removeOverlay()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        ServiceStateHolder.update { it.copy(isServiceRunning = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isCurrentlyCounting(): Boolean {
        return isScreenInteractive && isDeviceUnlocked && !isManualPaused && !isOnBreak
    }

    private fun startTickLoopIfActive() {
        handler.removeCallbacks(activeTickRunnable)
        if (isCurrentlyCounting()) {
            lastTickElapsedRealtime = SystemClock.elapsedRealtime()
            handler.postDelayed(activeTickRunnable, 1000L)
        }
    }

    private fun stopTickLoop() {
        handler.removeCallbacks(activeTickRunnable)
    }

    private fun flushActiveDelta() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsedRealtime).coerceIn(0L, 15_000L)
        lastTickElapsedRealtime = now
        if (delta > 0L && isCurrentlyCounting()) {
            cycleElapsedMs += delta
            repository.addActiveScreenDelta(delta, cycleElapsedMs, config)
        }
    }

    private fun checkBreakTrigger() {
        val workMs = config.workMinutes * 60_000L
        val now = SystemClock.elapsedRealtime()
        if (cycleElapsedMs >= workMs && !breakAlertDispatched && now >= snoozeUntilElapsedRealtime) {
            breakAlertDispatched = true
            dispatchBreakAlertNotification()
            ServiceStateHolder.update { it.copy(breakDueModalOpen = true) }
        }
    }

    private fun startBreak() {
        flushActiveDelta()
        stopTickLoop()
        notificationManager.cancel(BREAK_NOTIF_ID)
        isOnBreak = true
        breakRemainingSec = config.breakSeconds
        ServiceStateHolder.update {
            it.copy(
                isOnBreak = true,
                breakDueModalOpen = true,
                breakRemainingSec = breakRemainingSec
            )
        }
        handler.removeCallbacks(breakCountdownRunnable)
        handler.postDelayed(breakCountdownRunnable, 1000L)
        updateForegroundNotification()
    }

    private fun completeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        isOnBreak = false
        cycleElapsedMs = 0L
        breakAlertDispatched = false
        snoozeUntilElapsedRealtime = 0L
        repository.recordBreakCompleted(config)
        triggerHapticTap()
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update {
            it.copy(isOnBreak = false, breakDueModalOpen = false, breakRemainingSec = config.breakSeconds)
        }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun snoozeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        isOnBreak = false
        breakAlertDispatched = false
        snoozeUntilElapsedRealtime = SystemClock.elapsedRealtime() + 5 * 60_000L
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update { it.copy(isOnBreak = false, breakDueModalOpen = false) }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun skipBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        isOnBreak = false
        cycleElapsedMs = 0L
        breakAlertDispatched = false
        snoozeUntilElapsedRealtime = 0L
        repository.recordBreakSkipped(config)
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update { it.copy(isOnBreak = false, breakDueModalOpen = false) }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun emitState() {
        val today = repository.loadDailyRecord()
        val last7 = repository.loadLastNDays(7)
        ServiceStateHolder.update { prev ->
            prev.copy(
                isServiceRunning = true,
                isScreenOnAndUnlocked = isScreenInteractive && isDeviceUnlocked,
                isManualPaused = isManualPaused,
                isOnBreak = isOnBreak,
                breakRemainingSec = breakRemainingSec,
                cycleElapsedMs = cycleElapsedMs,
                todayRecord = today,
                last7Days = last7,
                config = config
            )
        }
    }

    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Screen-On Eye Care Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status showing active screen-on tracking"
            setShowBadge(false)
        }

        val breakChannel = NotificationChannel(
            BREAK_CHANNEL_ID,
            "20-20-20 Break Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when it is time to rest your eyes for 20 seconds"
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(monitorChannel)
        notificationManager.createNotificationChannel(breakChannel)
    }

    private fun buildMonitorNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val workMs = config.workMinutes * 60_000L
        val remainingMin = ((workMs - cycleElapsedMs).coerceAtLeast(0L) / 60_000L) + 1L

        val statusText = when {
            isOnBreak -> "20-20-20 eye rest in progress (${breakRemainingSec}s)"
            isManualPaused -> "Tracking paused manually"
            !isScreenInteractive || !isDeviceUnlocked -> "Screen off — timer paused (audio excluded)"
            else -> "Display On · Next 20-20-20 break in ~${remainingMin}m"
        }

        val takeBreakIntent = PendingIntent.getBroadcast(
            this,
            10,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_START_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("OptiRest Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Take Break", takeBreakIntent)
            .build()
    }

    private fun updateForegroundNotification() {
        notificationManager.notify(MONITOR_NOTIF_ID, buildMonitorNotification())
    }

    private fun dispatchBreakAlertNotification() {
        if (!config.notificationsEnabled) return

        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val startBreakPi = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_START_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozePi = PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_SNOOZE_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("20-20-20 Eye Break Due")
            .setContentText("Look 20 feet away for ${config.breakSeconds} seconds to relax eye focus.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(0, "Start ${config.breakSeconds}s Break", startBreakPi)
            .addAction(0, "Snooze 5m", snoozePi)

        if (config.soundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (config.vibrateEnabled) {
            triggerHapticTap()
        }

        notificationManager.notify(BREAK_NOTIF_ID, builder.build())
    }

    private fun triggerHapticTap() {
        try {
            val pattern = longArrayOf(0, 140, 90, 140)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
        }
    }
}
