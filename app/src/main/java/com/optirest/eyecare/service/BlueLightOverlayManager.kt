package com.optirest.eyecare.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager

class BlueLightOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun applyFilter(enabled: Boolean, warmthKelvin: Int, alphaPercent: Int) {
        if (!enabled || alphaPercent <= 0 || !Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }

        val color = computeTintColor(warmthKelvin, alphaPercent)

        if (overlayView == null) {
            val view = View(context).apply {
                setBackgroundColor(color)
            }
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(view, params)
                overlayView = view
            } catch (_: Exception) {
                overlayView = null
            }
        } else {
            overlayView?.setBackgroundColor(color)
        }
    }

    fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Already detached
            }
        }
        overlayView = null
    }

    private fun computeTintColor(kelvin: Int, alphaPercent: Int): Int {
        val t = ((kelvin - 1800).toFloat() / (6500 - 1800)).coerceIn(0f, 1f)
        val r = (255 - t * 25).toInt().coerceIn(0, 255)
        val g = (110 + t * 120).toInt().coerceIn(0, 255)
        val b = (25 + t * 200).toInt().coerceIn(0, 255)
        val a = ((alphaPercent.coerceIn(0, 65) / 100f) * 255f).toInt().coerceIn(0, 175)
        return Color.argb(a, r, g, b)
    }
}
