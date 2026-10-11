package com.angeset.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class IslandOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var island: LinearLayout? = null
    private var expanded = false

    override fun onCreate() {
        super.onCreate()
        val channelId = "dynamic_island_runtime"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Dynamic Island", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId).setContentTitle("Dynamic Island is active")
                .setContentText("Tap the island to expand it").setSmallIcon(android.R.drawable.ic_media_play).build()
        else Notification.Builder(this).setContentTitle("Dynamic Island is active")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(41, notification)
        if (Settings.canDrawOverlays(this)) showIsland()
        else stopSelf()
    }

    private fun showIsland() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 10, 24, 10)
            background = GradientDrawable().apply {
                setColor(Color.rgb(0, 0, 0))
                cornerRadius = 48f
                setStroke(1, Color.rgb(42, 42, 46))
            }
        }
        val title = TextView(this).apply {
            text = "●     Dynamic Island     ◖"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        pill.addView(title)
        island = pill
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 12
        }
        pill.setOnClickListener {
            expanded = !expanded
            title.text = if (expanded) "♫  Now Playing\nNo media detected yet\nTap to collapse" else "●     Dynamic Island     ◖"
            pill.setPadding(24, if (expanded) 22 else 10, 24, if (expanded) 22 else 10)
            params.width = if (expanded) (resources.displayMetrics.widthPixels * 0.78f).toInt() else WindowManager.LayoutParams.WRAP_CONTENT
            pill.background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = 36f; setStroke(1, Color.rgb(42,42,46)) }
            windowManager?.updateViewLayout(pill, params)
        }
        pill.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private var startPos = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startY = event.rawY; startPos = params.y }
                    MotionEvent.ACTION_MOVE -> {
                        params.y = (startPos + event.rawY - startY).toInt().coerceAtLeast(0)
                        windowManager?.updateViewLayout(pill, params)
                    }
                }
                return false
            }
        })
        windowManager?.addView(pill, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        island?.let { runCatching { windowManager?.removeView(it) } }
        island = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
