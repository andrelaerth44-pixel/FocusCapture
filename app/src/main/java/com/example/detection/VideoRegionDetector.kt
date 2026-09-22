package com.example.detection

import android.content.Context
import android.net.Uri

/**
 * Interface desacoplada para detecção da região ativa do vídeo.
 * Permite alternar perfeitamente entre HeuristicVideoRegionDetector e MLVideoRegionDetector.
 */
interface VideoRegionDetector {
    /**
     * Analisa o arquivo de vídeo temporário e retorna a região mais provável correspondente ao conteúdo de vídeo.
     */
    suspend fun detectRegion(context: Context, videoUri: Uri): DetectionResult
}
