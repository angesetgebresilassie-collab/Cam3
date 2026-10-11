package com.angeset.dynamicisland

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.app.AlertDialog
import android.os.Bundle
import android.media.MediaRecorder
import android.os.Environment
import java.io.File
import android.os.Build
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
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
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
            "screen" -> {
                add("Start screen recording") { val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; startActivityForResult(mgr.createScreenCaptureIntent(), 41) }
                add("Stop and save") { startService(android.content.Intent(this, ScreenRecordingService::class.java).setAction("STOP")); startService(android.content.Intent(this, IslandOverlayService::class.java).setAction("ISLAND_DONE")); status.text = "Stopped. Video is saved in app Movies storage." }
            }
            "sound" -> {
                add("Start sound recording") { startSoundRecording() }
                add("Stop and save") { stopSoundRecording() }
            }
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
                override fun onFinish() { status.text = "Timer complete"; startService(android.content.Intent(this@UtilityActivity, IslandOverlayService::class.java).setAction("ISLAND_DONE")); Toast.makeText(this@UtilityActivity, "Timer complete", Toast.LENGTH_LONG).show() }
            }.start()
        }.setNegativeButton("Cancel", null).show()
    }
    @Deprecated("Android screen-capture consent callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK && data != null) {
            val service = android.content.Intent(this, ScreenRecordingService::class.java).putExtra("resultCode", resultCode).putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Screen recording started"
        }
    }
    private fun startSoundRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 27)
            status.text = "Allow microphone access, then tap Start again"
            return
        }
        try {
            val folder = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            audioFile = File(folder, "sound-" + System.currentTimeMillis() + ".m4a")
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            status.text = "Recording…"
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            status.text = "Could not start: " + (e.message ?: "unknown error")
        }
    }
    private fun stopSoundRecording() {
        try { recorder?.stop(); status.text = "Saved " + (audioFile?.name ?: "audio file") }
        catch (_: Exception) { status.text = "Recording was too short to save" }
        finally { recorder?.release(); recorder = null; startService(android.content.Intent(this, IslandOverlayService::class.java).setAction("ISLAND_DONE")) }
    }
    private fun format(ms: Long) = String.format(Locale.US, "%02d:%02d.%1d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)
    override fun onDestroy() { handler.removeCallbacks(ticker); countdown?.cancel(); if (recorder != null) stopSoundRecording(); super.onDestroy() }
}
