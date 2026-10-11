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
import android.os.BatteryManager
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
    private var headline: TextView? = null
    private var details: TextView? = null
    private var expanded = false
    private lateinit var params: WindowManager.LayoutParams

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
        if (Settings.canDrawOverlays(this)) showIsland() else stopSelf()
    }

    private fun showIsland() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(25, 11, 25, 11)
            background = pillBackground()
            elevation = 12f
        }
        headline = TextView(this).apply {
            text = "●     Dynamic Island     ◖"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        details = TextView(this).apply {
            setTextColor(Color.rgb(220, 220, 226))
            textSize = 12f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 10, 0, 0)
            maxWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        }
        pill.addView(headline)
        pill.addView(details)
        island = pill
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 10
        }

        pill.setOnClickListener { toggleExpanded(pill) }
        pill.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private var startPos = 0
            private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startPos = params.y
                        moved = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawY - startY) > 8) moved = true
                        if (moved) {
                            params.y = (startPos + event.rawY - startY).toInt().coerceAtLeast(0)
                            runCatching { windowManager?.updateViewLayout(pill, params) }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> if (moved) return true
                }
                return false
            }
        })
        windowManager?.addView(pill, params)
    }

    private fun toggleExpanded(pill: LinearLayout) {
        expanded = !expanded
        if (expanded) {
            val prefs = getSharedPreferences("island_state", MODE_PRIVATE)
            val title = prefs.getString("notification_title", null)
            val bodyText = prefs.getString("notification_text", null)
            val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale).toString() + "%" else "—"
            val status = if (!title.isNullOrBlank()) {
                "LATEST NOTIFICATION\n" + title + "\n" + bodyText.orEmpty()
            } else {
                "No notifications yet\nGrant notification access to show previews"
            }
            headline?.text = "◉   Dynamic Island"
            details?.text = status + "\n\nBattery  " + percent
            details?.visibility = View.VISIBLE
            pill.setPadding(26, 20, 26, 20)
            params.width = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        } else {
            headline?.text = "●     Dynamic Island     ◖"
            details?.visibility = View.GONE
            pill.setPadding(25, 11, 25, 11)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
        }
        pill.background = pillBackground()
        runCatching { windowManager?.updateViewLayout(pill, params) }
    }

    private fun pillBackground() = GradientDrawable().apply {
        setColor(Color.rgb(4, 4, 6))
        cornerRadius = if (expanded) 38f else 60f
        setStroke(1, Color.rgb(50, 50, 56))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        island?.let { runCatching { windowManager?.removeView(it) } }
        island = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
