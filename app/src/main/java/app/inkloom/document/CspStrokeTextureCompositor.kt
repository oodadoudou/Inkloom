package app.inkloom.document

import android.graphics.Bitmap
import app.inkloom.engine.v5.tools.NativeRasterOps

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

/** Applies CSP Texture once to an already-combined stroke surface in one bounded V3 native pass. */
object CspStrokeTextureCompositor {
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
    ) {
        if (strokeBitmap.isRecycled || contentWidth <= 0 || contentHeight <= 0 || density <= 0f) return
        require(contentWidth <= strokeBitmap.width && contentHeight <= strokeBitmap.height)
        check(NativeRasterOps.applyCspStrokeTexture(
            stroke = strokeBitmap,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            originX = originX,
            originY = originY,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            texturePixels = material.pixels,
            textureWidth = material.width,
            textureHeight = material.height,
            density = density,
            scalePercent = scalePercent,
            rotationDegrees = rotationDegrees,
            offsetX = offsetX,
            offsetY = offsetY,
            filteringMode = filteringMode,
            brightness = brightness,
            contrast = contrast,
            reversed = reversed,
            emphasized = emphasized,
            compositeMode = compositeMode,
            alphaThresholdEnabled = alphaThresholdEnabled,
            alphaThreshold = alphaThreshold,
        )) { "Native CSP texture compositor rejected a compatible RGBA surface" }
    }
}
