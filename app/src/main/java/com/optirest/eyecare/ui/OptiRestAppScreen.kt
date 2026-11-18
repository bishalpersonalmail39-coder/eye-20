package com.optirest.eyecare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optirest.eyecare.data.AppConfig
import com.optirest.eyecare.data.DailyUsageRecord
import com.optirest.eyecare.service.EyeCareUiState
import com.optirest.eyecare.ui.theme.AmberComfort
import com.optirest.eyecare.ui.theme.ElevatedSurface
import com.optirest.eyecare.ui.theme.HairlineBorder
import com.optirest.eyecare.ui.theme.ObsidianBg
import com.optirest.eyecare.ui.theme.RoseAlert
import com.optirest.eyecare.ui.theme.TealBright
import com.optirest.eyecare.ui.theme.TealPrimary
import com.optirest.eyecare.ui.theme.TextPrimary
import com.optirest.eyecare.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptiRestAppScreen(
    uiState: EyeCareUiState,
    isIgnoringBatteryOpt: Boolean,
    canDrawOverlay: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTogglePause: () -> Unit,
    onStartBreak: () -> Unit,
    onCompleteBreak: () -> Unit,
    onSnoozeBreak: () -> Unit,
    onSkipBreak: () -> Unit,
    onResetCycle: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OptiRest",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ElevatedSurface,
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    "Overview" to Icons.Outlined.Visibility,
                    "Filter" to Icons.Outlined.BrightnessMedium,
                    "Reports" to Icons.Outlined.BarChart,
                    "Alerts" to Icons.Outlined.Notifications
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TealBright,
                            selectedTextColor = TealBright,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x1F14B8A6)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            when (selectedTab) {
                0 -> OverviewTab(
                    uiState = uiState,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onTogglePause = onTogglePause,
                    onStartBreak = onStartBreak,
                    onResetCycle = onResetCycle
                )

                1 -> FilterTab(
                    config = uiState.config,
                    canDrawOverlay = canDrawOverlay,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onUpdateConfig = onUpdateConfig
                )

                2 -> ReportsTab(
                    records = uiState.last7Days,
                    dailyLimitMinutes = uiState.config.dailyLimitMinutes
                )

                3 -> AlertsTab(
                    config = uiState.config,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onUpdateConfig = onUpdateConfig
                )
            }
        }
    }

    if (uiState.breakDueModalOpen || uiState.isOnBreak) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ElevatedSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = if (uiState.isOnBreak) "Look 20 Feet Away" else "20-20-20 Eye Break Due",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (uiState.isOnBreak) {
                            "Relax your eyes on a distant object and blink slowly."
                        } else {
                            "You have completed ${uiState.config.workMinutes} minutes of active screen-on time."
                        },
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${if (uiState.isOnBreak) uiState.breakRemainingSec else uiState.config.breakSeconds}s",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TealBright
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = if (uiState.isOnBreak) onCompleteBreak else onStartBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text(if (uiState.isOnBreak) "Finish Early" else "Start ${uiState.config.breakSeconds}s Break")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!uiState.isOnBreak) {
                        OutlinedButton(onClick = onSnoozeBreak) {
                            Text("Snooze 5m", color = TextPrimary)
                        }
                    }
                    OutlinedButton(onClick = onSkipBreak) {
                        Text("Skip", color = TextSecondary)
                    }
                }
            }
        )
    }
}

@Composable
private fun OverviewTab(
    uiState: EyeCareUiState,
    isIgnoringBatteryOpt: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTogglePause: () -> Unit,
    onStartBreak: () -> Unit,
    onResetCycle: () -> Unit
) {
    val workMs = (uiState.config.workMinutes * 60_000L).coerceAtLeast(60_000L)
    val remainingMs = (workMs - uiState.cycleElapsedMs).coerceAtLeast(0L)
    val progress = (uiState.cycleElapsedMs.toFloat() / workMs.toFloat()).coerceIn(0f, 1f)
    val today = uiState.todayRecord
    val strainScore = today?.avgStrainScore ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // System Guard Banner if Battery Optimization or Notification permission needs user action
        if (!isIgnoringBatteryOpt || !hasNotificationPermission) {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = AmberComfort)
                    Text(
                        text = "Background Reliability Setup",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allow battery optimization exemption so Android keeps OptiRest running across reboots and screen-on cycles.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isIgnoringBatteryOpt) {
                        Button(
                            onClick = onRequestBatteryWhitelist,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberComfort, contentColor = Color.Black)
                        ) {
                            Text("Ignore Battery Optimization", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!hasNotificationPermission) {
                        OutlinedButton(onClick = onRequestNotificationPermission) {
                            Text("Enable Alerts", fontSize = 12.sp, color = TealBright)
                        }
                    }
                }
            }
        }

        // Focal 20-20-20 Ring Card
        SectionCard {
            Text(
                text = when {
                    uiState.isOnBreak -> "Resting · 20-20-20 Break"
                    uiState.isManualPaused -> "Paused Manually"
                    uiState.isScreenOnAndUnlocked -> "Counting · Display On"
                    else -> "Paused · Screen Off"
                },
                fontSize = 12.sp,
                color = TealBright,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "20-20-20 Eye Protection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(180.dp)) {
                    drawArc(
                        color = Color(0x14FFFFFF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (uiState.isManualPaused) AmberComfort else TealPrimary,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Next break in", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = formatCountdown(remainingMs),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = "${formatCompact(uiState.cycleElapsedMs)} elapsed",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartBreak,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Take ${uiState.config.breakSeconds}s Break", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (uiState.isManualPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (uiState.isManualPaused) "Resume" else "Pause",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onResetCycle) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reset cycle", tint = TextSecondary)
                }
            }
        }

        // Eye Strain Monitor Card
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eye Strain Level", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = when {
                        strainScore >= 65 -> "High Strain"
                        strainScore >= 42 -> "Elevated"
                        strainScore >= 22 -> "Moderate"
                        else -> "Optimal"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        strainScore >= 65 -> RoseAlert
                        strainScore >= 42 -> AmberComfort
                        else -> TealBright
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$strainScore / 100",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (strainScore / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    strainScore >= 65 -> RoseAlert
                    strainScore >= 42 -> AmberComfort
                    else -> TealPrimary
                },
                trackColor = Color(0x14FFFFFF)
            )
        }

        // Daily Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("Active Screen Today", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatCompact(today?.activeScreenMs ?: 0L),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("Breaks Completed", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${today?.breaksCompleted ?: 0}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TealBright
                )
            }
        }

        // Boot & Battery Optimization Status
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = TealBright)
                    Text("Background & Boot Status", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                if (isIgnoringBatteryOpt) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = TealBright)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Auto-start on phone boot: ${if (uiState.config.autoStartOnBoot) "Enabled" else "Off"}",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = "Battery optimization: ${if (isIgnoringBatteryOpt) "Unrestricted (Ignored)" else "Optimized (Tap above to exempt)"}",
                fontSize = 13.sp,
                color = if (isIgnoringBatteryOpt) TealBright else AmberComfort
            )
            Text(
                text = "Off-screen music/audio: Automatically paused when screen turns off",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun FilterTab(
    config: AppConfig,
    canDrawOverlay: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard {
            Text(
                text = "System-Wide Blue Light Filter",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Applies a warm amber veil across all Android apps when Display Over Other Apps permission is granted.",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (!canDrawOverlay) {
                Button(
                    onClick = onRequestOverlayPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberComfort, contentColor = Color.Black)
                ) {
                    Text("Grant Display Over Apps Permission", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Blue-Light Overlay", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = config.filterEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(filterEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AmberComfort)
                    )
                }
            }
        }

        SectionCard {
            Text("Color Temperature: ${config.filterWarmthKelvin}K", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = config.filterWarmthKelvin.toFloat(),
                onValueChange = {
                    onUpdateConfig(config.copy(filterWarmthKelvin = it.toInt(), filterEnabled = true))
                },
                valueRange = 1800f..6500f,
                colors = SliderDefaults.colors(thumbColor = AmberComfort, activeTrackColor = AmberComfort)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Filter Intensity: ${config.filterAlphaPercent}%", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = config.filterAlphaPercent.toFloat(),
                onValueChange = {
                    onUpdateConfig(config.copy(filterAlphaPercent = it.toInt(), filterEnabled = it > 0f))
                },
                valueRange = 0f..65f,
                colors = SliderDefaults.colors(thumbColor = AmberComfort, activeTrackColor = AmberComfort)
            )
        }
    }
}

@Composable
private fun ReportsTab(
    records: List<DailyUsageRecord>,
    dailyLimitMinutes: Int
) {
    val totalMin = records.sumOf { (it.activeScreenMs / 60_000L).toInt() }
    val breaksDone = records.sumOf { it.breaksCompleted }
    val breaksSkip = records.sumOf { it.breaksSkipped }
    val maxMins = (records.maxOfOrNull { (it.activeScreenMs / 60_000L).toInt() } ?: 60)
        .coerceAtLeast(dailyLimitMinutes.coerceAtMost(240))
        .coerceAtLeast(30)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard {
            Text("7-Day Screen Time Trend", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Active display-on minutes recorded per day", fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(20.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val count = records.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.55f).coerceAtMost(48f)

                records.forEachIndexed { idx, rec ->
                    val mins = (rec.activeScreenMs / 60_000f)
                    val ratio = (mins / maxMins.toFloat()).coerceIn(0f, 1f)
                    val barHeight = (size.height * ratio).coerceAtLeast(if (mins > 0f) 6f else 2f)
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val top = size.height - barHeight

                    drawRoundRect(
                        color = if (mins > dailyLimitMinutes) AmberComfort else TealPrimary,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                records.forEach { rec ->
                    Text(
                        text = rec.dateKey.takeLast(5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("7-Day Total", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${totalMin / 60}h ${totalMin % 60}m",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
            SectionCard(modifier = Modifier.weight(1f)) {
                Text("Breaks Taken / Skipped", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$breaksDone / $breaksSkip",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TealBright
                )
            }
        }
    }
}

@Composable
private fun AlertsTab(
    config: AppConfig,
    isIgnoringBatteryOpt: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard {
            Text("20-20-20 Interval Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(14.dp))

            Text("Active Screen Interval: ${config.workMinutes} min", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = config.workMinutes.toFloat(),
                onValueChange = { onUpdateConfig(config.copy(workMinutes = it.toInt())) },
                valueRange = 5f..60f,
                colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Break Duration: ${config.breakSeconds} sec", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = config.breakSeconds.toFloat(),
                onValueChange = { onUpdateConfig(config.copy(breakSeconds = it.toInt())) },
                valueRange = 10f..300f,
                colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary)
            )
        }

        SectionCard {
            Text("Boot, Battery & Alerts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            SettingSwitchRow(
                title = "Start automatically on phone boot",
                checked = config.autoStartOnBoot,
                onCheckedChange = { onUpdateConfig(config.copy(autoStartOnBoot = it)) }
            )
            SettingSwitchRow(
                title = "Break reminder notifications",
                checked = config.notificationsEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(notificationsEnabled = it)) }
            )
            SettingSwitchRow(
                title = "Notification alert sound",
                checked = config.soundEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(soundEnabled = it)) }
            )
            SettingSwitchRow(
                title = "Vibration on break reminder",
                checked = config.vibrateEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(vibrateEnabled = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (!isIgnoringBatteryOpt) {
                Button(
                    onClick = onRequestBatteryWhitelist,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberComfort, contentColor = Color.Black)
                ) {
                    Text("Exempt From Battery Optimization", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!hasNotificationPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Post Notifications", color = TealBright)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TealPrimary)
        )
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}

private fun formatCompact(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hrs > 0 -> "${hrs}h ${"%02d".format(mins)}m"
        mins > 0 -> "${mins}m ${"%02d".format(secs)}s"
        else -> "${secs}s"
    }
}
