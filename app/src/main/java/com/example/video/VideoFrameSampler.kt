package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoFrameSampler {

    /**
     * Extrai uma lista de bitmaps de amostragem em resolução reduzida (ex: 270x480)
     * distribuídos uniformemente pelo vídeo para análise de movimento e geometria.
     * Consumo mínimo de memória: cada bitmap tem menos de 500KB.
     */
    suspend fun sampleFrames(
        context: Context,
        videoUri: Uri,
        sampleCount: Int = 10,
        targetWidth: Int = 270,
        targetHeight: Int = 480
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 1000L

            val stepMs = if (sampleCount > 1) durationMs / (sampleCount + 1) else durationMs / 2

            for (i in 1..sampleCount) {
                val timeUs = (i * stepMs * 1000L).coerceIn(0L, durationMs * 1000L)
                val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetWidth,
                            targetHeight
                        )
                    } catch (_: Exception) {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                            val scaled = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
                            if (scaled != raw) raw.recycle()
                            scaled
                        }
                    }
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                        val scaled = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
                        if (scaled != raw) raw.recycle()
                        scaled
                    }
                }

                if (bitmap != null) {
                    bitmaps.add(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        bitmaps
    }

    /**
     * Extrai um único frame nítido de referência para visualização na tela de corte.
     */
    suspend fun extractPreviewFrame(
        context: Context,
        videoUri: Uri,
        timeMs: Long = 1000L,
        maxWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val raw = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            if (raw != null && raw.width > maxWidth) {
                val scale = maxWidth.toFloat() / raw.width.toFloat()
                val targetH = (raw.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(raw, maxWidth, targetH, true)
                if (scaled != raw) raw.recycle()
                scaled
            } else {
                raw
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
