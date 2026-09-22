package com.example.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.detection.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoCropProcessor {
    private const val TAG = "VideoCropProcessor"
    private const val TIMEOUT_US = 10_000L

    /**
     * Realiza o recorte físico do vídeo preservando sincronia de áudio e gerando MP4 na resolução recortada.
     * Reporta progresso percentual (0.0f a 1.0f) para a interface.
     */
    suspend fun processCrop(
        context: Context,
        sourceUri: Uri,
        cropRect: CropRect,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val metadata = VideoMetadata.extract(context, sourceUri)
        val sourceW = metadata.displayWidth
        val sourceH = metadata.displayHeight

        val pixelRect = cropRect.toPixelRect(sourceW, sourceH)
        val targetW = pixelRect.width
        val targetH = pixelRect.height

        Log.d(TAG, "Iniciando corte: Original ${sourceW}x${sourceH} -> Alvo ${targetW}x${targetH} (x=${pixelRect.x}, y=${pixelRect.y})")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

        try {
            extractor.setDataSource(context, sourceUri, null)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.e(TAG, "Nenhuma trilha de vídeo encontrada")
                return@withContext false
            }

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Configura Encoder de Vídeo (H.264)
            val outVideoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, (sourceW * sourceH * 3).coerceIn(3_000_000, 10_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoderMime = MediaFormat.MIMETYPE_VIDEO_AVC
            encoder = MediaCodec.createEncoderByType(encoderMime)
            encoder.configure(outVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Configura Decoder de Vídeo
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoFormat, null, null, 0)
            decoder.start()

            extractor.selectTrack(videoTrackIndex)

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var isMuxerStarted = false

            // Copia trilha de áudio diretamente se existir
            if (audioTrackIndex != -1 && audioFormat != null) {
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            val durationUs = (metadata.durationMs * 1000L).coerceAtLeast(1L)
            var isDecoderEOS = false
            var isEncoderEOS = false
            val bufferInfo = MediaCodec.BufferInfo()

            val maxInputBufferSize = 1024 * 1024
            val tempBuffer = ByteBuffer.allocateDirect(maxInputBufferSize)

            var decodedFrames = 0L

            while (!isEncoderEOS) {
                // 1. Alimenta o Decoder com dados do Extractor
                if (!isDecoderEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isDecoderEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Extrai frames decodificados e encaminha para o Encoder recortando
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // Envia sinal de EOS para o encoder
                        val encInIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (encInIndex >= 0) {
                            encoder.queueInputBuffer(encInIndex, 0, 0, bufferInfo.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    } else {
                        val image = decoder.getOutputImage(outIndex)
                        if (image != null) {
                            decodedFrames++
                            val progress = (bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.95f)
                            onProgress(progress)

                            // Alimenta o encoder com a sub-região recortada
                            val encInIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                            if (encInIndex >= 0) {
                                val encBuffer = encoder.getInputBuffer(encInIndex)
                                if (encBuffer != null) {
                                    encBuffer.clear()
                                    // Preenchimento de YUV cortado
                                    cropAndFillYuv(image, encBuffer, pixelRect, targetW, targetH)
                                    encoder.queueInputBuffer(
                                        encInIndex,
                                        0,
                                        encBuffer.position(),
                                        bufferInfo.presentationTimeUs,
                                        0
                                    )
                                }
                            }
                            image.close()
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                }

                // 3. Lê saída comprimida do Encoder e grava no MediaMuxer
                val encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (isMuxerStarted) {
                        Log.e(TAG, "Formato do encoder mudou após inicialização do muxer")
                    } else {
                        val newFormat = encoder.outputFormat
                        muxerVideoTrack = muxer.addTrack(newFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                } else if (encOutIndex >= 0) {
                    val outBuffer = encoder.getOutputBuffer(encOutIndex)
                    if (outBuffer != null && isMuxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        muxer.writeSampleData(muxerVideoTrack, outBuffer, bufferInfo)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEncoderEOS = true
                    }
                    encoder.releaseOutputBuffer(encOutIndex, false)
                }
            }

            // 4. Copia os samples de áudio diretamente para manter o som original sincronizado
            if (audioTrackIndex != -1 && muxerAudioTrack != -1 && isMuxerStarted) {
                extractor.unselectTrack(videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val audioBufferInfo = MediaCodec.BufferInfo()
                val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)

                while (true) {
                    val sampleSize = extractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = extractor.sampleTime
                    audioBufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                    extractor.advance()
                }
            }

            onProgress(1.0f)
            Log.d(TAG, "Processamento de vídeo finalizado com sucesso. Frames decodificados: $decodedFrames")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante processamento de crop nativo", e)
            false
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Copia as linhas da sub-região de corte (Y, U, V) da imagem decodificada para o buffer de entrada do encoder.
     */
    private fun cropAndFillYuv(
        image: android.media.Image,
        destBuffer: ByteBuffer,
        pixelRect: com.example.detection.PixelRect,
        targetW: Int,
        targetH: Int
    ) {
        val planes = image.planes
        if (planes.size < 3) return

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        // 1. Copia o plano Y
        val startX = pixelRect.x.coerceIn(0, image.width - 1)
        val startY = pixelRect.y.coerceIn(0, image.height - 1)

        val rowBytes = ByteArray(targetW)
        for (row in 0 until targetH) {
            val srcRow = (startY + row).coerceIn(0, image.height - 1)
            yBuffer.position(srcRow * yRowStride + startX * yPixelStride)
            if (yPixelStride == 1) {
                yBuffer.get(rowBytes, 0, targetW)
                destBuffer.put(rowBytes)
            } else {
                for (col in 0 until targetW) {
                    val p = yBuffer.get((srcRow * yRowStride) + ((startX + col) * yPixelStride))
                    destBuffer.put(p)
                }
            }
        }

        // 2. Copia os planos UV (subamostrados 2x2 para YUV420)
        val uvW = targetW / 2
        val uvH = targetH / 2
        val uvStartX = startX / 2
        val uvStartY = startY / 2

        val uBuffer = uPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        val vBuffer = vPlane.buffer
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        // U plane
        for (row in 0 until uvH) {
            val srcRow = (uvStartY + row).coerceIn(0, image.height / 2 - 1)
            for (col in 0 until uvW) {
                val srcCol = (uvStartX + col).coerceIn(0, image.width / 2 - 1)
                val u = uBuffer.get(srcRow * uRowStride + srcCol * uPixelStride)
                destBuffer.put(u)
            }
        }

        // V plane
        for (row in 0 until uvH) {
            val srcRow = (uvStartY + row).coerceIn(0, image.height / 2 - 1)
            for (col in 0 until uvW) {
                val srcCol = (uvStartX + col).coerceIn(0, image.width / 2 - 1)
                val v = vBuffer.get(srcRow * vRowStride + srcCol * vPixelStride)
                destBuffer.put(v)
            }
        }
    }
}
