package com.example.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "ScreenRecordingService"
        const val CHANNEL_ID = "focus_capture_screen_record"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.recording.ACTION_START"
        const val ACTION_STOP = "com.example.recording.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_DENSITY = "extra_density"
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var outputFile: File? = null
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())
    private var recordingSeconds = 0L
    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                recordingSeconds++
                outputFile?.let {
                    ScreenRecordingManager.updateState(ScreenRecordingState.Recording(recordingSeconds, it))
                }
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                val density = intent.getIntExtra(EXTRA_DENSITY, 420)

                if (resultCode != 0 && data != null) {
                    val initialNotification = buildNotification("Gravando tela...", 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            initialNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, initialNotification)
                    }

                    startRecording(resultCode, data, width, height, density)
                } else {
                    ScreenRecordingManager.updateState(
                        ScreenRecordingState.Error("Dados de permissão de tela inválidos")
                    )
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent, width: Int, height: Int, density: Int) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(cacheDir, "recordings").apply { if (!exists()) mkdirs() }
            outputFile = File(storageDir, "capture_$timeStamp.mp4")

            // Ajusta dimensões para serem pares (requisito de encoders H.264)
            val encWidth = if (width % 2 != 0) width - 1 else width
            val encHeight = if (height % 2 != 0) height - 1 else height

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(encWidth, encHeight)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(8 * 1024 * 1024)
            recorder.setOutputFile(outputFile!!.absolutePath)
            recorder.prepare()

            val mp = mediaProjectionManager?.getMediaProjection(resultCode, data)
            if (mp == null) {
                ScreenRecordingManager.updateState(ScreenRecordingState.Error("Não foi possível obter MediaProjection"))
                stopSelf()
                return
            }
            mediaProjection = mp

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopRecording()
                    }
                }, handler)
            }

            virtualDisplay = mp.createVirtualDisplay(
                "FocusCaptureVirtualDisplay",
                encWidth,
                encHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            isRecording = true
            recordingSeconds = 0
            handler.post(tickerRunnable)

            outputFile?.let {
                ScreenRecordingManager.updateState(ScreenRecordingState.Recording(0, it))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar gravação de tela", e)
            ScreenRecordingManager.updateState(
                ScreenRecordingState.Error("Falha ao iniciar gravador: ${e.localizedMessage}")
            )
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        handler.removeCallbacks(tickerRunnable)

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop warning (duração muito curta?)", e)
        }

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder release warning", e)
        }
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "VirtualDisplay release warning", e)
        }
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaProjection stop warning", e)
        }
        mediaProjection = null

        outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                ScreenRecordingManager.updateState(ScreenRecordingState.Finished(file))
            } else {
                ScreenRecordingManager.updateState(
                    ScreenRecordingState.Error("O arquivo de gravação não foi gerado ou está vazio")
                )
            }
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gravação de Tela - FocusCapture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação ativa com tempo decorrido e controle de parada"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, seconds: Long): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeString = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusCapture: Gravando [$timeString]")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Parar Gravação",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)
        val notification = buildNotification("Gravando tela do vídeo. Toque em Parar quando terminar.", recordingSeconds)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
