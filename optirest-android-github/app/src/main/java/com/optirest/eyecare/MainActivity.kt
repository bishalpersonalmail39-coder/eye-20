package com.optirest.eyecare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.optirest.eyecare.data.AppConfig
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.service.EyeCareForegroundService
import com.optirest.eyecare.service.ScreenFilterService
import com.optirest.eyecare.service.ScreenFilterStateHolder
import com.optirest.eyecare.service.ServiceStateHolder
import com.optirest.eyecare.ui.OptiRestAppScreen
import com.optirest.eyecare.ui.theme.OptiRestTheme
import com.optirest.eyecare.util.BatteryOptimizationHelper

class MainActivity : ComponentActivity() {

    private lateinit var repository: UsageRepository

    private var isIgnoringBatteryOpt by mutableStateOf(false)
    private var canDrawOverlay by mutableStateOf(false)
    private var hasUsageAccess by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = UsageRepository(this)
        refreshSystemPermissions()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        sendServiceAction(EyeCareForegroundService.ACTION_START_OR_SYNC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val uiState by ServiceStateHolder.uiState.collectAsState()
            val filterState by ScreenFilterStateHolder.state.collectAsState()

            OptiRestTheme {
                OptiRestAppScreen(
                    uiState = uiState,
                    filterState = filterState,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    canDrawOverlay = canDrawOverlay,
                    hasUsageAccess = hasUsageAccess,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                    },
                    onRequestOverlayPermission = {
                        BatteryOptimizationHelper.requestOverlayPermission(this)
                    },
                    onRequestUsageAccess = {
                        BatteryOptimizationHelper.requestUsageStatsPermission(this)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onTogglePause = { sendServiceAction(EyeCareForegroundService.ACTION_TOGGLE_PAUSE) },
                    onStartBreak = { sendServiceAction(EyeCareForegroundService.ACTION_START_BREAK) },
                    onCompleteBreak = { sendServiceAction(EyeCareForegroundService.ACTION_COMPLETE_BREAK) },
                    onSnoozeBreak = { sendServiceAction(EyeCareForegroundService.ACTION_SNOOZE_BREAK) },
                    onSkipBreak = { sendServiceAction(EyeCareForegroundService.ACTION_SKIP_BREAK) },
                    onDismissPrompt = { sendServiceAction(EyeCareForegroundService.ACTION_DISMISS_PROMPT) },
                    onResetCycle = { sendServiceAction(EyeCareForegroundService.ACTION_RESET_CYCLE) },
                    onToggleSampleHistory = { sendServiceAction(EyeCareForegroundService.ACTION_TOGGLE_SAMPLE_HISTORY) },
                    onClearHistory = {
                        repository.clearAllHistory()
                        sendServiceAction(EyeCareForegroundService.ACTION_START_OR_SYNC)
                    },
                    onUpdateConfig = { nextConfig ->
                        applyUpdatedConfig(nextConfig)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemPermissions()
        sendServiceAction(EyeCareForegroundService.ACTION_SYNC_CONFIG)
    }

    private fun applyUpdatedConfig(nextConfig: AppConfig) {
        repository.saveConfig(nextConfig)
        sendServiceAction(EyeCareForegroundService.ACTION_SYNC_CONFIG)

        val scheduledEvening = nextConfig.autoEveningSchedule &&
            repository.isWithinTimeWindow(nextConfig.sunsetStart, nextConfig.morningEnd)
        val shouldOverlayRun = (nextConfig.filterEnabled || scheduledEvening) &&
            (nextConfig.filterExtraDimPercent > 0 || nextConfig.filterBlueCutPercent > 0) &&
            canDrawOverlay

        val filterIntent = Intent(this, ScreenFilterService::class.java).apply {
            action = if (shouldOverlayRun) {
                ScreenFilterService.ACTION_APPLY_FILTER
            } else {
                ScreenFilterService.ACTION_DISABLE_FILTER
            }
        }
        if (shouldOverlayRun) {
            ContextCompat.startForegroundService(this, filterIntent)
        } else {
            try {
                startService(filterIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshSystemPermissions() {
        isIgnoringBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        canDrawOverlay = BatteryOptimizationHelper.canDrawOverlays(this)
        hasUsageAccess = BatteryOptimizationHelper.hasUsageStatsPermission(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, EyeCareForegroundService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
