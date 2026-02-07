package com.example.dimmer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import androidx.core.app.NotificationCompat

// Constants moved inside the class or companion for cleaner scoping
private const val DIMMER_CHANNEL = "DimmerServiceChannel"
private const val SET_DIM_LEVEL = "SET_DIM_LEVEL"
private const val ACTION_STOP_DIMMER = "ACTION_STOP_DIMMER"
private const val DIM_LEVEL_EXTRA = "DIM_LEVEL"

class DimmerService : Service() {

    private var overlayView: View? = null
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        // Handle incoming commands
        intent?.let {
            when (it.action) {
                ACTION_STOP_DIMMER -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                SET_DIM_LEVEL -> {
                    val level = it.getIntExtra(DIM_LEVEL_EXTRA, 50)
                    if (overlayView == null) addOverlayView()
                    updateDimmingLevel(level)
                    startForeground(1, createNotification(level))
                }
                else -> {
                    // Default start (from MainActivity)
                    if (overlayView == null) addOverlayView()
                    startForeground(1, createNotification(50))
                }
            }
        }

        return START_STICKY // Keep it running until explicitly stopped
    }

    private fun addOverlayView() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        // Ensure we cover the "notch" or "hole punch" area at the top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          layoutParams.fitInsetsTypes = 0
            layoutParams.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // This ensures the view starts at the absolute (0,0) of the physical screen
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 0

        overlayView = View(this).apply {
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnApplyWindowInsetsListener { _, insets ->
                    // Return CONSUMED so the system doesn't try to add padding for the nav bar
                    WindowInsets.CONSUMED
                }
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }
    private fun updateDimmingLevel(dimLevel: Int) {
        overlayView?.setBackgroundColor(Color.argb(getAlpha(dimLevel), 0, 0, 0))
    }

    private fun createNotification(dimLevel: Int): Notification {
        // Build the notification
        val builder = NotificationCompat.Builder(this, DIMMER_CHANNEL)
            .setContentTitle("Dimmer Active")
            .setContentText("Current level: $dimLevel%")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Use system icon for testing
            .setOngoing(true)
            .setSilent(true) // Don't beep every time we update level
            .addAction(0, "Stop", createStopIntent())
            
        val levels = listOf(25, 50, 75)
        for (level in levels) {
            if (level != dimLevel) {
                builder.addAction(0, "$level%", createDimLevelIntent(level))
            }
        }

        return builder.build()
    }

    private fun createStopIntent(): PendingIntent {
        val intent = Intent(this, DimmerService::class.java).apply { action = ACTION_STOP_DIMMER }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createDimLevelIntent(level: Int): PendingIntent {
        val intent = Intent(this, DimmerService::class.java).apply {
            action = SET_DIM_LEVEL
            putExtra(DIM_LEVEL_EXTRA, level)
        }
        return PendingIntent.getService(this, level, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getAlpha(dimLevel: Int): Int = (2.55 * dimLevel).toInt().coerceIn(0, 255)

    override fun onDestroy() {
        overlayView?.let {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            overlayView = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            DIMMER_CHANNEL, "Dimmer Service", NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}
