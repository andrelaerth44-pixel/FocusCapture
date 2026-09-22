package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.detection.CropRect
import com.example.detection.DetectionResult
import com.example.detection.HeuristicVideoRegionDetector
import com.example.recording.ScreenRecordingManager
import com.example.recording.ScreenRecordingState
import com.example.storage.HistoryItem
import com.example.storage.HistoryManager
import com.example.storage.MediaStoreExporter
import com.example.video.VideoCropProcessor
import com.example.video.VideoFrameSampler
import com.example.video.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class AppScreen {
    object Home : AppScreen()
    data class Recording(val seconds: Long, val file: File?) : AppScreen()
    data class Analyzing(val statusMessage: String = "Analisando mapa de movimento do vídeo...") : AppScreen()
    data class ReviewAndCrop(
        val videoUri: Uri,
        val previewBitmap: Bitmap?,
        val detectionResult: DetectionResult,
        val currentCropRect: CropRect,
        val metadata: VideoMetadata
    ) : AppScreen()
    data class Processing(val progress: Float) : AppScreen()
    data class Result(
        val originalUri: Uri,
        val croppedFile: File,
        val exportedUri: Uri?,
        val cropRect: CropRect,
        val metadata: VideoMetadata
    ) : AppScreen()
    object History : AppScreen()
}

class FocusCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val detector = HeuristicVideoRegionDetector()

    init {
        HistoryManager.init(application)
        observeRecordingState()
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            ScreenRecordingManager.recordingState.collect { state ->
                when (state) {
                    is ScreenRecordingState.Idle -> {
                        if (_currentScreen.value is AppScreen.Recording) {
                            _currentScreen.value = AppScreen.Home
                        }
                    }
                    is ScreenRecordingState.Recording -> {
                        _currentScreen.value = AppScreen.Recording(state.durationSeconds, state.tempFile)
                    }
                    is ScreenRecordingState.Finished -> {
                        onVideoSourceReady(Uri.fromFile(state.recordedFile))
                    }
                    is ScreenRecordingState.Error -> {
                        Toast.makeText(getApplication(), state.message, Toast.LENGTH_LONG).show()
                        _currentScreen.value = AppScreen.Home
                    }
                }
            }
        }
    }

    fun startRecording(context: Context, resultCode: Int, data: Intent, width: Int, height: Int, density: Int) {
        ScreenRecordingManager.startService(context, resultCode, data, width, height, density)
    }

    fun stopRecording(context: Context) {
        ScreenRecordingManager.stopService(context)
    }

    fun onVideoSourceReady(videoUri: Uri) {
        viewModelScope.launch {
            _currentScreen.value = AppScreen.Analyzing("Detectando região do reprodutor de vídeo...")

            val context = getApplication<Application>()
            val metadata = VideoMetadata.extract(context, videoUri)
            val detection = detector.detectRegion(context, videoUri)
            val previewBitmap = VideoFrameSampler.extractPreviewFrame(context, videoUri, timeMs = 1500L)

            _currentScreen.value = AppScreen.ReviewAndCrop(
                videoUri = videoUri,
                previewBitmap = previewBitmap,
                detectionResult = detection,
                currentCropRect = detection.cropRect,
                metadata = metadata
            )
        }
    }

    fun updateCropRect(newRect: CropRect) {
        val state = _currentScreen.value as? AppScreen.ReviewAndCrop ?: return
        _currentScreen.value = state.copy(currentCropRect = newRect)
    }

    fun applyPreset(aspectRatio: Float?, state: AppScreen.ReviewAndCrop) {
        if (aspectRatio == null) {
            // Livre / Tela cheia
            updateCropRect(state.detectionResult.cropRect)
            return
        }

        val meta = state.metadata
        val videoW = meta.displayWidth.toFloat()
        val videoH = meta.displayHeight.toFloat()
        val currentCenterY = (state.currentCropRect.top + state.currentCropRect.bottom) / 2f
        val currentCenterX = (state.currentCropRect.left + state.currentCropRect.right) / 2f

        // Calcula novo retângulo centralizado baseado na proporção
        val targetBoxH: Float
        val targetBoxW: Float

        if (aspectRatio < 1.0f) {
            // Vertical (ex: 9:16)
            targetBoxH = 0.85f
            val pixelH = targetBoxH * videoH
            val pixelW = pixelH * aspectRatio
            targetBoxW = (pixelW / videoW).coerceIn(0.2f, 1.0f)
        } else {
            // Horizontal (ex: 16:9)
            targetBoxW = 0.90f
            val pixelW = targetBoxW * videoW
            val pixelH = pixelW / aspectRatio
            targetBoxH = (pixelH / videoH).coerceIn(0.2f, 1.0f)
        }

        val newLeft = (currentCenterX - targetBoxW / 2f).coerceIn(0f, 1f - targetBoxW)
        val newRight = newLeft + targetBoxW
        val newTop = (currentCenterY - targetBoxH / 2f).coerceIn(0f, 1f - targetBoxH)
        val newBottom = newTop + targetBoxH

        updateCropRect(CropRect(newLeft, newTop, newRight, newBottom))
    }

    fun executeCrop(reviewState: AppScreen.ReviewAndCrop) {
        viewModelScope.launch {
            _currentScreen.value = AppScreen.Processing(0.0f)

            val context = getApplication<Application>()
            val timeStamp = System.currentTimeMillis()
            val outputDir = File(context.cacheDir, "cropped_videos").apply { if (!exists()) mkdirs() }
            val outputFile = File(outputDir, "focus_crop_$timeStamp.mp4")

            val success = VideoCropProcessor.processCrop(
                context = context,
                sourceUri = reviewState.videoUri,
                cropRect = reviewState.currentCropRect,
                outputFile = outputFile,
                onProgress = { progress ->
                    _currentScreen.value = AppScreen.Processing(progress)
                }
            )

            if (success && outputFile.exists() && outputFile.length() > 0) {
                // Exporta automaticamente para a galeria
                val title = "FocusCapture_$timeStamp"
                val galleryUri = MediaStoreExporter.exportToGallery(context, outputFile, title)

                val pixelCrop = reviewState.currentCropRect.toPixelRect(reviewState.metadata.displayWidth, reviewState.metadata.displayHeight)
                HistoryManager.addItem(
                    context,
                    HistoryItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        filePath = outputFile.absolutePath,
                        timestamp = timeStamp,
                        width = pixelCrop.width,
                        height = pixelCrop.height,
                        confidence = reviewState.detectionResult.confidence
                    )
                )

                _currentScreen.value = AppScreen.Result(
                    originalUri = reviewState.videoUri,
                    croppedFile = outputFile,
                    exportedUri = galleryUri,
                    cropRect = reviewState.currentCropRect,
                    metadata = reviewState.metadata
                )
            } else {
                Toast.makeText(context, "Erro ao processar recorte do vídeo", Toast.LENGTH_LONG).show()
                _currentScreen.value = reviewState
            }
        }
    }

    fun shareVideo(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Vídeo Recortado"))
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Vídeo Recortado"))
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateHome() {
        ScreenRecordingManager.reset()
        _currentScreen.value = AppScreen.Home
    }
}
