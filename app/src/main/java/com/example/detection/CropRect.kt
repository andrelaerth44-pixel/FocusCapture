package com.example.detection

/**
 * Retângulo de corte normalizado (valores relativos de 0.0f a 1.0f).
 * Permite que as coordenadas permaneçam invariantes independentemente da resolução da tela ou do vídeo.
 */
data class CropRect(
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val right: Float = 1.0f,
    val bottom: Float = 1.0f
) {
    init {
        require(left in 0.0f..1.0f) { "left deve estar entre 0.0 e 1.0" }
        require(top in 0.0f..1.0f) { "top deve estar entre 0.0 e 1.0" }
        require(right in 0.0f..1.0f) { "right deve estar entre 0.0 e 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom deve estar entre 0.0 e 1.0" }
        require(right > left) { "right deve ser estritamente maior que left" }
        require(bottom > top) { "bottom deve ser estritamente maior que top" }
    }

    val widthRatio: Float get() = right - left
    val heightRatio: Float get() = bottom - top

    fun toPixelRect(sourceWidth: Int, sourceHeight: Int): PixelRect {
        val pxLeft = (left * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val pxTop = (top * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val pxRight = (right * sourceWidth).toInt().coerceIn(pxLeft + 1, sourceWidth)
        val pxBottom = (bottom * sourceHeight).toInt().coerceIn(pxTop + 1, sourceHeight)

        // Dimensões de vídeo precisam ser números pares para compatibilidade com H.264/H.265
        val rawWidth = pxRight - pxLeft
        val rawHeight = pxBottom - pxTop
        val evenWidth = if (rawWidth % 2 != 0) (rawWidth - 1).coerceAtLeast(2) else rawWidth
        val evenHeight = if (rawHeight % 2 != 0) (rawHeight - 1).coerceAtLeast(2) else rawHeight

        return PixelRect(
            x = pxLeft,
            y = pxTop,
            width = evenWidth,
            height = evenHeight
        )
    }

    companion object {
        val FullScreen = CropRect(0.0f, 0.0f, 1.0f, 1.0f)
    }
}

data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
