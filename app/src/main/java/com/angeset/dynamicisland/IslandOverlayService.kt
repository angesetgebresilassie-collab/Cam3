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
    private var charging = false
    private var hasNotification = false
    private var utilityControls: LinearLayout? = null
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
        if (Settings.canDrawOverlays(this)) {
            showIsland()
            val b = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val s = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
            hasNotification = !getSharedPreferences("island_state", MODE_PRIVATE).getString("notification_title", null).isNullOrBlank()
            registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (charging) showForEvent("charging") else if (hasNotification) showForEvent("notification")
        } else stopSelf()
    }

    private fun showIsland() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 8, 20, 8)
            background = pillBackground()
            elevation = 12f
            visibility = View.GONE
        }
        headline = TextView(this).apply {
            text = ""
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
        utilityControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE; setPadding(0, 4, 0, 0) }
        addControl(controls!!, "⏮") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        addControl(controls!!, "▶Ⅱ") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        addControl(controls!!, "⏭") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        addControl(controls!!, "×") { collapse(pill) }
        addControl(utilityControls!!, "◉") { openUtility("screen") }
        addControl(utilityControls!!, "◷") { openUtility("stopwatch") }
        addControl(utilityControls!!, "⏱") { openUtility("timer") }
        addControl(utilityControls!!, "🎙") { openUtility("sound") }
        pill.addView(headline)
        pill.addView(details)
        pill.addView(controls)
        pill.addView(utilityControls)
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

        pill.setOnClickListener { if (expanded) collapse(pill) else toggleExpanded(pill) }
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

    private fun addControl(row: LinearLayout, label: String, action: () -> Unit) {
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
        row.addView(button, LinearLayout.LayoutParams(0, 44, 1f))
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
            // Android commonly exposes battery level as an integer. Keep one decimal in the UI;
            // the decimal is a formatted estimate, not a hardware-guaranteed precision reading.
            val percent = if (level >= 0 && scale > 0) {
                String.format(Locale.US, "%.1f%%", (level.toDouble() * 100.0 / scale).coerceIn(0.0, 100.0))
            } else "—"
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val watts = if (voltageMv > 0 && currentUa != Int.MIN_VALUE && currentUa != 0) {
                String.format(Locale.US, "%.2f W", abs(currentUa.toDouble()) * voltageMv / 1_000_000_000.0)
            } else "Unavailable"
            val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL
            val status = if (!title.isNullOrBlank()) {
                "LATEST NOTIFICATION\n$title\n${bodyText.orEmpty()}"
            } else {
                "No notifications yet\nGrant notification access to show previews"
            }
            headline?.text = if (charging) "⚡" else if (!title.isNullOrBlank()) "●" else "⌄"
            details?.text = if (charging) "Battery $percent  •  $watts" else status
            details?.visibility = View.VISIBLE
            controls?.visibility = if (!charging && !title.isNullOrBlank() && (title.contains("music", true) || title.contains("playing", true))) View.VISIBLE else View.GONE
            utilityControls?.visibility = View.VISIBLE
            pill.setPadding(20, 15, 20, 12)
            params.width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        } else {
            headline?.text = ""
            details?.visibility = View.GONE
            controls?.visibility = View.GONE
            utilityControls?.visibility = View.GONE
            pill.setPadding(20, 8, 20, 8)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            if (!charging && !hasNotification) island?.visibility = View.GONE
        }
        pill.background = pillBackground()
        runCatching { windowManager?.updateViewLayout(pill, params) }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val s = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val now = s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
            if (now && !charging) showForEvent("charging")
            charging = now
            if (!charging && !expanded && !hasNotification) island?.visibility = View.GONE
        }
    }
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideEvent = Runnable { if (!expanded && !charging) { island?.visibility = View.GONE; hasNotification = false } }
    private fun showForEvent(reason: String) {
        island?.visibility = View.VISIBLE
        headline?.text = when (reason) { "charging" -> "⚡"; "notification" -> "●"; "screen" -> "◉"; "stopwatch" -> "◷"; "timer" -> "⏱"; "sound" -> "🎙"; else -> "" }
        uiHandler.removeCallbacks(hideEvent)
        if (!charging) uiHandler.postDelayed(hideEvent, 6000L)
        runCatching { windowManager?.updateViewLayout(island, params) }
    }
    private fun collapse(pill: LinearLayout) {
        expanded = false; details?.visibility = View.GONE; controls?.visibility = View.GONE; utilityControls?.visibility = View.GONE
        headline?.text = ""; pill.setPadding(20, 8, 20, 8); params.width = WindowManager.LayoutParams.WRAP_CONTENT
        if (!charging && !hasNotification) pill.visibility = View.GONE
        pill.background = pillBackground(); runCatching { windowManager?.updateViewLayout(pill, params) }
    }
    private fun openUtility(mode: String) {
        showForEvent(mode)
        startActivity(Intent(this, UtilityActivity::class.java).putExtra("mode", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun pillBackground() = GradientDrawable().apply {
        setColor(Color.rgb(4, 4, 6))
        cornerRadius = if (expanded) 38f else 60f
        setStroke(1, Color.rgb(50, 50, 56))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ISLAND_NOTIFICATION" -> { hasNotification = true; showForEvent("notification") }
            "ISLAND_TIMER" -> showForEvent("timer")
            "ISLAND_STOPWATCH" -> showForEvent("stopwatch")
            "ISLAND_SOUND" -> showForEvent("sound")
            "ISLAND_SCREEN" -> showForEvent("screen")
        }
        return START_STICKY
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        uiHandler.removeCallbacksAndMessages(null)
        island?.let { runCatching { windowManager?.removeView(it) } }
        island = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
