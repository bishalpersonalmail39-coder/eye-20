package com.optirest.eyecare.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.service.EyeCareForegroundService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val config = UsageRepository(context).loadConfig()
            if (config.autoStartOnBoot) {
                val serviceIntent = Intent(context, EyeCareForegroundService::class.java).apply {
                    this.action = EyeCareForegroundService.ACTION_START_OR_SYNC
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
