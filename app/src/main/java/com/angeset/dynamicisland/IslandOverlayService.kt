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
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class IslandOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var island: LinearLayout? = null
    private var headline: TextView? = null
    private var details: TextView? = null
    private var controls: LinearLayout? = null
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
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 12, 0, 0)
        }
        addControl("⏮") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        addControl("▶ / Ⅱ") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        addControl("⏭") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        addControl("×") { expanded = true; toggleExpanded(pill, forceCollapse = true) }
        pill.addView(headline)
        pill.addView(details)
        pill.addView(controls)
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
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawY - startY) > 8) moved = true
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

    private fun addControl(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { action() }
            minWidth = 0
            minimumWidth = 0
            setPadding(8, 0, 8, 0)
        }
        controls?.addView(button, LinearLayout.LayoutParams(0, 48, 1f))
    }

    private fun sendMediaKey(keyCode: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val now = android.os.SystemClock.uptimeMillis()
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    private fun toggleExpanded(pill: LinearLayout, forceCollapse: Boolean = false) {
        expanded = if (forceCollapse) false else !expanded
        if (expanded) {
            val prefs = getSharedPreferences("island_state", MODE_PRIVATE)
            val title = prefs.getString("notification_title", null)
            val bodyText = prefs.getString("notification_text", null)
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
            val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val percent = if (level >= 0 && scale > 0) {
                val base = level.toDouble() * 100.0 / scale
                val estimatedFull = if (base > 0.0 && chargeCounter > 0) chargeCounter / (base / 100.0) else 0.0
                val precise = if (estimatedFull > 0) chargeCounter * 100.0 / estimatedFull else base
                String.format(Locale.US, "%.1f%%", precise.coerceIn(0.0, 100.0))
            } else "—"
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val watts = if (voltageMv > 0 && currentUa != Int.MIN_VALUE && currentUa != 0) {
                String.format(Locale.US, "%.2f W", abs(currentUa.toDouble()) * voltageMv / 1_000_000_000.0)
            } else "Unavailable"
            val charging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in
                listOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
            val status = if (!title.isNullOrBlank()) {
                "LATEST NOTIFICATION\n$title\n${bodyText.orEmpty()}"
            } else {
                "No notifications yet\nGrant notification access to show previews"
            }
            headline?.text = if (charging) "⚡  Charging" else "◉   Dynamic Island"
            details?.text = "$status\n\nBattery  $percent\nCharging power  $watts"
            details?.visibility = View.VISIBLE
            controls?.visibility = View.VISIBLE
            pill.setPadding(26, 20, 26, 16)
            params.width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        } else {
            headline?.text = "●     Dynamic Island     ◖"
            details?.visibility = View.GONE
            controls?.visibility = View.GONE
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
