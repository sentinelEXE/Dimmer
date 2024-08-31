package com.example.dimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat


private const val DIMMER_CHANNEL: String = "DimmerServiceChannel"
private const val SET_DIM_LEVEL: String = "SET_DIM_LEVEL"
private const val ACTION_STOP_DIMMER: String = "ACTION_STOP_DIMMER"
private const val DIM_LEVEL_EXTRA: String = "DIM_LEVEL"
private const val DIMMER_CHANNEL_NAME: String = "Dimmer Service Channel"
private const val DIMMER_CHANNEL_DESCRIPTION: String = "Notification channel for the Dimmer service"

class DimmerService : Service() {

    private var overlayView: View? = null
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundNotification()

        if (overlayView == null) {
            addOverlayView()
        }
        intent?.let {
            when (it.action) {
                SET_DIM_LEVEL -> {
                    val dimLevel = it.getIntExtra(DIM_LEVEL_EXTRA, 50)

                    updateDimmingLevel(dimLevel)
                    updateNotification(dimLevel)
                }
                ACTION_STOP_DIMMER -> {
                    removeOverlayView()
                    stopForeground(true)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
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

        layoutParams.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        layoutParams.gravity = Gravity.TOP or Gravity.START
        overlayView = View(this).apply {
            setBackgroundColor(Color.argb(128, 0, 0, 0))  // Adjust alpha for dimming level
        }
        overlayView!!.setOnApplyWindowInsetsListener { _, insets ->
            insets
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun updateDimmingLevel(dimLevel: Int) {
        overlayView?.apply { setBackgroundColor(Color.argb(getAlpha(dimLevel), 0, 0, 0))}
    }

    private fun removeOverlayView() {
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            DIMMER_CHANNEL,
            DIMMER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = DIMMER_CHANNEL_DESCRIPTION
            setShowBadge(false)

        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        startForeground(1, createNotification(50))
    }

    private fun updateNotification(dimLevel: Int) {
        notificationManager.notify(1, createNotification(dimLevel))
    }

    private fun createNotification(dimLevel: Int): Notification {
        val notification = NotificationCompat.Builder(this, DIMMER_CHANNEL)
            .setContentText("Current dim level: $dimLevel%")
            .setSmallIcon(R.drawable.ic_dimmer)
            .setOngoing(true)
            .addAction(R.drawable.ic_dimmer, "Stop", createRemoveIntent())
        addDimLevelIntentActions(notification, dimLevel)
        return notification.build()
    }

    private fun addDimLevelIntentActions(notification: NotificationCompat.Builder, dimLevel: Int) {
        if (dimLevel != 25) {
            notification.addAction(R.drawable.ic_dimmer, "25%", createDimLevelIntent(25))
        }
        if (dimLevel != 50) {
            notification.addAction(R.drawable.ic_dimmer, "50%", createDimLevelIntent(50))
        }
        if (dimLevel != 75) {
            notification.addAction(R.drawable.ic_dimmer, "75%", createDimLevelIntent(75))
        }
    }

    private fun createDimLevelIntent(dimLevel: Int): PendingIntent {
        val intent = Intent(this, DimmerService::class.java).apply {
            action = SET_DIM_LEVEL
            putExtra(DIM_LEVEL_EXTRA, dimLevel)
        }
        return PendingIntent.getService(this, dimLevel, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createRemoveIntent(): PendingIntent {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_STOP_DIMMER
        }
        return PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getAlpha(dimLevel: Int): Int {
        return (255f * (dimLevel / 100f)).toInt()
    }

}