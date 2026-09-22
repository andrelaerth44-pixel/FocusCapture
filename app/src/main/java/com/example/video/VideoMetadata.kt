package com.example.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int = 0
) {
    val displayWidth: Int get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight: Int get() = if (rotation == 90 || rotation == 270) width else height
    val aspectRatio: Float get() = if (displayHeight > 0) displayWidth.toFloat() / displayHeight.toFloat() else 1.0f

    companion object {
        fun extract(context: Context, uri: Uri): VideoMetadata {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                val rawWidth = widthStr?.toIntOrNull() ?: 1080
                val rawHeight = heightStr?.toIntOrNull() ?: 1920
                val duration = durationStr?.toLongOrNull() ?: 0L
                val rotation = rotationStr?.toIntOrNull() ?: 0

                VideoMetadata(
                    width = rawWidth,
                    height = rawHeight,
                    durationMs = duration,
                    rotation = rotation
                )
            } catch (e: Exception) {
                VideoMetadata(width = 1080, height = 1920, durationMs = 0L)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }
}
