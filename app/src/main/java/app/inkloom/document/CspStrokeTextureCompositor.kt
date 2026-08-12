package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.Color

/** Decoded CSP paper material retained separately from the brush-tip cache. */
data class CspTextureMaterial(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size >= width * height)
    }

    companion object {
        fun decode(path: String): CspTextureMaterial? {
            val bitmap = BrushTipTextureDecoder.decodeSampled(path, BrushTipTextureDecoder.MAX_RENDER_TEXTURE_DIMENSION) ?: return null
            return try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                CspTextureMaterial(bitmap.width, bitmap.height, pixels)
            } finally {
                bitmap.recycle()
            }
        }
    }
}

/** Applies CSP Texture once to an already-combined stroke surface. This is deliberately separate
 * from [BrushTipTextureDecoder], whose output represents Apply by each plot. */
object CspStrokeTextureCompositor {
    class Workspace {
        internal var pixels = IntArray(0)
        internal var textureMasks = FloatArray(0)

        internal fun ensure(pixelCount: Int, needsTextureMasks: Boolean) {
            if (pixels.size < pixelCount) pixels = IntArray(pixelCount)
            if (needsTextureMasks && textureMasks.size < pixelCount) textureMasks = FloatArray(pixelCount)
        }
    }

    fun applyInPlace(
        strokeBitmap: Bitmap,
        contentWidth: Int,
        contentHeight: Int,
        originX: Int,
        originY: Int,
        pageWidth: Int,
        pageHeight: Int,
        material: CspTextureMaterial,
        density: Float,
        scalePercent: Float,
        rotationDegrees: Float,
        offsetX: Float,
        offsetY: Float,
        filteringMode: Int,
        brightness: Float,
        contrast: Float,
        reversed: Boolean,
        emphasized: Boolean,
        compositeMode: Int,
        alphaThresholdEnabled: Boolean,
        alphaThreshold: Float,
        workspace: Workspace,
    ) {
        if (strokeBitmap.isRecycled || contentWidth <= 0 || contentHeight <= 0 || density <= 0f) return
        require(contentWidth <= strokeBitmap.width && contentHeight <= strokeBitmap.height)
        val countLong = contentWidth.toLong() * contentHeight
        if (countLong > Int.MAX_VALUE) return
        val count = countLong.toInt()
        val outline = compositeMode == CspTextureComposite.OUTLINE
        val sampler = TextureSampling.prepare(
            pageWidth,
            pageHeight,
            material.width,
            material.height,
            scalePercent,
            rotationDegrees,
            offsetX,
            offsetY,
        )
        workspace.ensure(count, outline)
        strokeBitmap.getPixels(workspace.pixels, 0, contentWidth, 0, 0, contentWidth, contentHeight)
        if (outline) {
            for (localY in 0 until contentHeight) for (localX in 0 until contentWidth) {
                workspace.textureMasks[localY * contentWidth + localX] = textureMask(
                    material, sampler, originX + localX, originY + localY, filteringMode,
                    brightness, contrast, reversed, emphasized,
                )
            }
        }
        val cutoff = alphaThreshold.coerceIn(0f, 1f) * 255f
        for (localY in 0 until contentHeight) for (localX in 0 until contentWidth) {
            val index = localY * contentWidth + localX
            val pixel = workspace.pixels[index]
            val sourceAlpha = Color.alpha(pixel)
            if (sourceAlpha == 0) continue
            val pageX = originX + localX
            val pageY = originY + localY
            val texture = if (outline) workspace.textureMasks[index] else textureMask(
                material, sampler, pageX, pageY, filteringMode,
                brightness, contrast, reversed, emphasized,
            )
            val textureEdge = if (outline) {
                val left = if (localX > 0) workspace.textureMasks[index - 1] else textureMaskAt(material, sampler, pageX - 1, pageY, filteringMode, brightness, contrast, reversed, emphasized)
                val right = if (localX + 1 < contentWidth) workspace.textureMasks[index + 1] else textureMaskAt(material, sampler, pageX + 1, pageY, filteringMode, brightness, contrast, reversed, emphasized)
                val top = if (localY > 0) workspace.textureMasks[index - contentWidth] else textureMaskAt(material, sampler, pageX, pageY - 1, filteringMode, brightness, contrast, reversed, emphasized)
                val bottom = if (localY + 1 < contentHeight) workspace.textureMasks[index + contentWidth] else textureMaskAt(material, sampler, pageX, pageY + 1, filteringMode, brightness, contrast, reversed, emphasized)
                (kotlin.math.abs(right - left) + kotlin.math.abs(bottom - top)).coerceIn(0f, 1f)
            } else 0f
            val composed = CspTextureComposite.density(sourceAlpha / 255f, texture, density, compositeMode, textureEdge)
            val rawAlpha = (composed * 255f).toInt().coerceIn(0, 255)
            val outputAlpha = if (alphaThresholdEnabled) if (rawAlpha > cutoff) 255 else 0 else rawAlpha
            workspace.pixels[index] = Color.argb(outputAlpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        }
        strokeBitmap.setPixels(workspace.pixels, 0, contentWidth, 0, 0, contentWidth, contentHeight)
    }

    private fun textureMask(
        material: CspTextureMaterial,
        sampler: TextureSampling.PreparedSampler,
        x: Int,
        y: Int,
        filteringMode: Int,
        brightness: Float,
        contrast: Float,
        reversed: Boolean,
        emphasized: Boolean,
    ): Float = textureMaskAt(material, sampler, x, y, filteringMode, brightness, contrast, reversed, emphasized)

    private fun textureMaskAt(
        material: CspTextureMaterial,
        sampler: TextureSampling.PreparedSampler,
        x: Int,
        y: Int,
        filteringMode: Int,
        brightness: Float,
        contrast: Float,
        reversed: Boolean,
        emphasized: Boolean,
    ): Float = TextureSampling.mask(
        sampler.sampleEffectiveLuminance(material.pixels, x, y, filteringMode),
        brightness,
        contrast,
        reversed,
        emphasized,
    )
}
