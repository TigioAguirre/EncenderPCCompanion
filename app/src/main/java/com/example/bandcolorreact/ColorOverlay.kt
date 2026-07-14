package com.example.bandcolorreact

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Dibuja un rectángulo de color sobre toda la pantalla usando el permiso
 * SYSTEM_ALERT_WINDOW (superponerse a otras apps), y lo retira solo después
 * de un rato. Así funciona aunque tu app esté en segundo plano.
 */
object ColorOverlay {

    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, playingColor: Boolean, durationMs: Long = 1200L) {
        if (!Settings.canDrawOverlays(context)) return

        handler.post {
            removeInternal(context)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val view = View(context).apply {
                setBackgroundColor(if (playingColor) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(view, params)
            overlayView = view

            handler.postDelayed({ removeInternal(context) }, durationMs)
        }
    }

    private fun removeInternal(context: Context) {
        overlayView?.let {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
    }
}
