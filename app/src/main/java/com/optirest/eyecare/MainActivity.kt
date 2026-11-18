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
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.service.EyeCareForegroundService
import com.optirest.eyecare.service.ServiceStateHolder
import com.optirest.eyecare.ui.OptiRestAppScreen
import com.optirest.eyecare.ui.theme.OptiRestTheme
import com.optirest.eyecare.util.BatteryOptimizationHelper

class MainActivity : ComponentActivity() {

    private lateinit var repository: UsageRepository

    private var isIgnoringBatteryOpt by mutableStateOf(false)
    private var canDrawOverlay by mutableStateOf(false)
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

        // Ensure Foreground Service is started when user opens the app
        sendServiceAction(EyeCareForegroundService.ACTION_START_OR_SYNC)

        // Prompt Android 13+ notification permission on first launch if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            OptiRestTheme {
                val uiState by ServiceStateHolder.uiState.collectAsState()
                OptiRestAppScreen(
                    uiState = uiState,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    canDrawOverlay = canDrawOverlay,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                    },
                    onRequestOverlayPermission = {
                        BatteryOptimizationHelper.requestOverlayPermission(this)
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
                    onResetCycle = { sendServiceAction(EyeCareForegroundService.ACTION_RESET_CYCLE) },
                    onUpdateConfig = { nextConfig ->
                        repository.saveConfig(nextConfig)
                        sendServiceAction(EyeCareForegroundService.ACTION_SYNC_CONFIG)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemPermissions()
    }

    private fun refreshSystemPermissions() {
        isIgnoringBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        canDrawOverlay = BatteryOptimizationHelper.canDrawOverlays(this)
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
