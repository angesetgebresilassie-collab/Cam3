package com.angeset.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class ScreenRecordingService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("data") as? Intent
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val channel = "screen_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
            startForeground(52, Notification.Builder(this, channel)
                .setContentTitle("Screen capture active").setContentText("Use the app to stop and save")
                .setSmallIcon(android.R.drawable.presence_video_online).build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val output = File(dir, "screen-" + System.currentTimeMillis() + ".mp4")
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width - width % 2, height - height % 2)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(6_000_000)
                setOutputFile(output.absolutePath)
                prepare()
            }
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            display = projection?.createVirtualDisplay("DynamicIslandCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder?.surface, null, null)
            recorder?.start()
        } catch (_: Exception) {
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopCapture() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        display?.release(); display = null
        projection?.stop(); projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { stopCapture(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
