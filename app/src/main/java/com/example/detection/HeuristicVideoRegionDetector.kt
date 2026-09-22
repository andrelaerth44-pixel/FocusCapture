package com.example.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.video.VideoFrameSampler
import com.example.video.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class HeuristicVideoRegionDetector : VideoRegionDetector {

    override suspend fun detectRegion(context: Context, videoUri: Uri): DetectionResult = withContext(Dispatchers.Default) {
        val metadata = VideoMetadata.extract(context, videoUri)
        val frames = VideoFrameSampler.sampleFrames(context, videoUri, sampleCount = 8, targetWidth = 180, targetHeight = 320)

        if (frames.size < 2) {
            // Se não foi possível extrair amostras suficientes, retorna tela cheia com confiança baixa
            return@withContext DetectionResult(
                cropRect = CropRect.FullScreen,
                confidence = 0.50f,
                description = "Amostras insuficientes. Seleção manual recomendada.",
                requiresManualConfirmation = true
            )
        }

        val width = frames[0].width
        val height = frames[0].height

        // 1. Matriz de variação de movimento temporal por linha (Y) e coluna (X)
        val rowMotion = FloatArray(height)
        val colMotion = FloatArray(width)

        // Compara pares consecutivos de quadros
        val numPairs = frames.size - 1
        for (p in 0 until numPairs) {
            val f1 = frames[p]
            val f2 = frames[p + 1]

            for (y in 0 until height) {
                var rowDiff = 0f
                for (x in 0 until width) {
                    val p1 = f1.getPixel(x, y)
                    val p2 = f2.getPixel(x, y)

                    val lum1 = 0.299f * Color.red(p1) + 0.587f * Color.green(p1) + 0.114f * Color.blue(p1)
                    val lum2 = 0.299f * Color.red(p2) + 0.587f * Color.green(p2) + 0.114f * Color.blue(p2)

                    val diff = abs(lum1 - lum2)
                    rowDiff += diff
                    colMotion[x] += diff
                }
                rowMotion[y] += (rowDiff / width)
            }
        }

        // Média de movimento por linha e por coluna
        for (y in 0 until height) rowMotion[y] /= numPairs
        for (x in 0 until width) colMotion[x] /= (numPairs * height)

        // Limpa os bitmaps da memória para poupar RAM imediatamente
        frames.forEach { if (!it.isRecycled) it.recycle() }

        // Média global de movimento para limiar
        val avgRowMotion = rowMotion.average().toFloat()
        val motionThreshold = (avgRowMotion * 0.25f).coerceAtLeast(3.0f)

        // 2. Busca das bordas verticais (Topo e Base)
        var topIdx = 0
        var bottomIdx = height - 1

        // Detecta topo (ignora até 20% do topo caso seja status bar ou cabeçalho estático)
        val maxTopSearch = (height * 0.25f).toInt()
        for (y in 0 until maxTopSearch) {
            if (rowMotion[y] >= motionThreshold) {
                topIdx = y
                break
            }
        }

        // Detecta base (ignora até 25% da base caso seja barra de navegação ou controles de vídeo estáticos)
        val minBottomSearch = (height * 0.75f).toInt()
        for (y in (height - 1) downTo minBottomSearch) {
            if (rowMotion[y] >= motionThreshold) {
                bottomIdx = y
                break
            }
        }

        // 3. Busca das bordas laterais (Esquerda e Direita)
        var leftIdx = 0
        var rightIdx = width - 1

        val avgColMotion = colMotion.average().toFloat()
        val colThreshold = (avgColMotion * 0.20f).coerceAtLeast(2.0f)

        val maxSideSearch = (width * 0.35f).toInt()
        for (x in 0 until maxSideSearch) {
            if (colMotion[x] >= colThreshold) {
                leftIdx = x
                break
            }
        }
        for (x in (width - 1) downTo (width - maxSideSearch)) {
            if (colMotion[x] >= colThreshold) {
                rightIdx = x
                break
            }
        }

        // Validação mínima de segurança: a área de vídeo não pode ser menor que 20% da tela
        if ((rightIdx - leftIdx) < width * 0.25f) {
            leftIdx = 0
            rightIdx = width - 1
        }
        if ((bottomIdx - topIdx) < height * 0.25f) {
            topIdx = 0
            bottomIdx = height - 1
        }

        var normLeft = (leftIdx.toFloat() / width.toFloat()).coerceIn(0.0f, 0.40f)
        var normTop = (topIdx.toFloat() / height.toFloat()).coerceIn(0.0f, 0.30f)
        var normRight = (rightIdx.toFloat() / width.toFloat()).coerceIn(0.60f, 1.0f)
        var normBottom = (bottomIdx.toFloat() / height.toFloat()).coerceIn(0.70f, 1.0f)

        // 4. Heurística de Encaixe em Proporções Padronizadas (Aspect Ratio Snapping)
        val detectedAspect = ((normRight - normLeft) * metadata.displayWidth) /
                ((normBottom - normTop) * metadata.displayHeight)

        var description = "Região ativa de vídeo detectada."
        var confidence = 0.82f

        // Caso TikTok / Reels / Shorts (Vertical 9:16 = 0.5625)
        if (abs(detectedAspect - 0.5625f) < 0.08f || (metadata.displayHeight > metadata.displayWidth && normTop > 0.02f)) {
            // Barra de status comum (top ~4% a 8%) e barra inferior (~4% a 8%)
            description = "Player vertical 9:16 detectado (TikTok/Reels/Shorts). Barras de status e navegação excluídas."
            confidence = 0.94f
        }
        // Caso YouTube / Player horizontal (16:9 = 1.777)
        else if (abs(detectedAspect - 1.777f) < 0.12f) {
            description = "Player widescreen 16:9 detectado. Bordas e controles excluídos."
            confidence = 0.92f
        }
        // Caso Quadrado 1:1
        else if (abs(detectedAspect - 1.0f) < 0.10f) {
            description = "Player quadrado 1:1 detectado."
            confidence = 0.88f
        } else {
            description = "Região retangular contínua de movimento detectada."
            confidence = 0.80f
        }

        // Assegura limites válidos
        val finalCrop = CropRect(
            left = normLeft,
            top = normTop,
            right = normRight,
            bottom = normBottom
        )

        DetectionResult(
            cropRect = finalCrop,
            confidence = confidence,
            description = description,
            requiresManualConfirmation = confidence < 0.85f
        )
    }
}
