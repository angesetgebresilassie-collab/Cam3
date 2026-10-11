package com.angeset.dynamicisland

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 246, 250)
        window.navigationBarColor = Color.rgb(246, 246, 250)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 54, 28, 28)
            setBackgroundColor(Color.rgb(246, 246, 250))
        }
        root.addView(TextView(this).apply {
            text = "Dynamic Island"
            textSize = 30f
            setTextColor(Color.rgb(20, 20, 24))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "A little island. A lot happening."
            textSize = 15f
            setTextColor(Color.rgb(105, 105, 116))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 30)
        }, LinearLayout.LayoutParams(-1, -2))
        val preview = TextView(this).apply {
            text = "●   DYNAMIC ISLAND\n\nTap to preview the floating pill\n\n♪  Now Playing     ▰"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(22, 34, 22, 34)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 210))
        root.addView(TextView(this).apply {
            text = "Enable the overlay to keep the island visible over other apps. You control when it runs."
            textSize = 14f
            setTextColor(Color.rgb(90, 90, 100))
            setPadding(2, 24, 2, 20)
        })
        root.addView(Button(this).apply {
            text = "Enable Dynamic Island"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startForegroundService(Intent(this@MainActivity, IslandOverlayService::class.java))
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Notification access"
            setOnClickListener { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}
