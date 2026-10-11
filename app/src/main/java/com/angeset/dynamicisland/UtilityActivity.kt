package com.angeset.dynamicisland

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class UtilityActivity : Activity() {
    private lateinit var status: TextView
    private var startAt = 0L
    private var elapsed = 0L
    private var running = false
    private var countdown: CountDownTimer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (running) status.text = "Stopwatch  " + format(elapsed + android.os.SystemClock.elapsedRealtime() - startAt)
            handler.postDelayed(this, 100)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "tools"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24, 32, 24, 24) }
        root.addView(TextView(this).apply { text = when(mode) { "timer" -> "Timer"; "stopwatch" -> "Stopwatch"; "screen" -> "Screen capture"; "sound" -> "Sound recorder"; else -> "Tools" }; textSize = 26f })
        status = TextView(this).apply { text = "Ready"; textSize = 18f; setPadding(0, 20, 0, 20) }
        root.addView(status)
        fun add(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } })
        }
        when (mode) {
            "stopwatch" -> {
                add("Start / Pause") { if (running) { elapsed += android.os.SystemClock.elapsedRealtime() - startAt; running = false } else { startAt = android.os.SystemClock.elapsedRealtime(); running = true } }
                add("Reset") { running = false; elapsed = 0; status.text = "00:00.0" }
            }
            "timer" -> add("Set countdown") { showTimerDialog() }
            "screen" -> add("Open device screen capture") { Toast.makeText(this, "Use the system Screen Record tile to start a capture.", Toast.LENGTH_LONG).show() }
            "sound" -> add("Sound recorder") { Toast.makeText(this, "Sound recording setup will request microphone permission.", Toast.LENGTH_LONG).show() }
            else -> {
                add("Stopwatch") { startActivity(android.content.Intent(this, UtilityActivity::class.java).putExtra("mode", "stopwatch")) }
                add("Timer") { startActivity(android.content.Intent(this, UtilityActivity::class.java).putExtra("mode", "timer")) }
                add("Screen recording") { startActivity(android.content.Intent(this, UtilityActivity::class.java).putExtra("mode", "screen")) }
                add("Sound recorder") { startActivity(android.content.Intent(this, UtilityActivity::class.java).putExtra("mode", "sound")) }
            }
        }
        add("Close") { finish() }
        setContentView(root)
        handler.post(ticker)
    }
    private fun showTimerDialog() {
        val input = EditText(this).apply { hint = "Seconds"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText("60") }
        AlertDialog.Builder(this).setTitle("Countdown timer").setView(input).setPositiveButton("Start") { _, _ ->
            val seconds = input.text.toString().toLongOrNull()?.coerceIn(1, 86400) ?: 60L
            countdown?.cancel()
            countdown = object : CountDownTimer(seconds * 1000, 100) {
                override fun onTick(ms: Long) { status.text = "Timer  " + String.format(Locale.US, "%02d:%04.1f", ms / 60000, (ms % 60000) / 1000.0) }
                override fun onFinish() { status.text = "Timer complete"; Toast.makeText(this@UtilityActivity, "Timer complete", Toast.LENGTH_LONG).show() }
            }.start()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun format(ms: Long) = String.format(Locale.US, "%02d:%02d.%1d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)
    override fun onDestroy() { handler.removeCallbacks(ticker); countdown?.cancel(); super.onDestroy() }
}
