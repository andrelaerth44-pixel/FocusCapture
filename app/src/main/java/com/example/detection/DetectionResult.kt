package com.example.detection

/**
 * Resultado da análise visual de detecção da região de vídeo.
 *
 * @param cropRect Retângulo de corte detectado (normalizado entre 0.0 e 1.0)
 * @param confidence Pontuação de confiança entre 0.0f (0%) e 1.0f (100%)
 * @param description Explicação resumida da detecção (ex: "Detectado player 9:16 vertical sem barras")
 * @param requiresManualConfirmation True se a confiança for média/baixa e exigir validação do usuário
 */
data class DetectionResult(
    val cropRect: CropRect,
    val confidence: Float,
    val description: String = "",
    val requiresManualConfirmation: Boolean = confidence < 0.85f
)
