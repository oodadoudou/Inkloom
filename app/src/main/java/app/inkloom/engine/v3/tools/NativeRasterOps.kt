package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Rect

/** Coarse-grained JNI entry points for bounded raster work that Android's software Canvas cannot
 * execute with RuntimeShader. Pixel buffers never cross into Kotlin. */
internal object NativeRasterOps {
    fun compositePigment(
        base: Bitmap,
        fresh: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        freshRatio: Float,
        sourceStrength: Float,
        localPigment: Boolean,
        perceptual: Boolean,
        brightnessCorrection: Float,
    ): Boolean = nativeCompositePigment(
        base,
        fresh,
        left,
        top,
        width,
        height,
        freshRatio,
        sourceStrength,
        localPigment,
        perceptual,
        brightnessCorrection,
    )

    fun blur(source: Bitmap, destination: Bitmap, width: Int, height: Int, radius: Float): Boolean =
        nativeBlur(source, destination, width, height, radius)

    fun alphaBounds(source: Bitmap): Rect? = nativeAlphaBounds(source).toRectOrNull()

    fun isAlphaEmpty(source: Bitmap): Boolean = nativeIsAlphaEmpty(source)

    fun invertAlpha(source: Bitmap, destination: Bitmap): Boolean = nativeInvertAlpha(source, destination)

    fun boundaryAlpha(source: Bitmap, destination: Bitmap): Boolean = nativeBoundaryAlpha(source, destination)

    fun morphAlpha(source: Bitmap, destination: Bitmap, radius: Int, dilate: Boolean): Boolean =
        nativeMorphAlpha(source, destination, radius, dilate)

    fun floodFill(
        source: Bitmap,
        destination: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int,
        areaScalingPixels: Int,
    ): Rect? = nativeFloodFill(
        source,
        destination,
        startX,
        startY,
        tolerance,
        closeGapPixels,
        areaScalingPixels,
    ).toRectOrNull()

    fun compositeDualBrush(
        primary: Bitmap,
        secondary: Bitmap,
        output: Bitmap,
        region: Rect,
        modeCode: Int,
        applyRgb: Boolean,
    ): Boolean = nativeCompositeDualBrush(
        primary,
        secondary,
        output,
        region.left,
        region.top,
        region.right,
        region.bottom,
        modeCode,
        applyRgb,
    )

    private external fun nativeCompositePigment(
        base: Bitmap,
        fresh: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        freshRatio: Float,
        sourceStrength: Float,
        localPigment: Boolean,
        perceptual: Boolean,
        brightnessCorrection: Float,
    ): Boolean

    private external fun nativeBlur(
        source: Bitmap,
        destination: Bitmap,
        width: Int,
        height: Int,
        radius: Float,
    ): Boolean

    private external fun nativeAlphaBounds(source: Bitmap): IntArray
    private external fun nativeIsAlphaEmpty(source: Bitmap): Boolean
    private external fun nativeInvertAlpha(source: Bitmap, destination: Bitmap): Boolean
    private external fun nativeBoundaryAlpha(source: Bitmap, destination: Bitmap): Boolean
    private external fun nativeMorphAlpha(source: Bitmap, destination: Bitmap, radius: Int, dilate: Boolean): Boolean
    private external fun nativeFloodFill(
        source: Bitmap,
        destination: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int,
        areaScalingPixels: Int,
    ): IntArray
    private external fun nativeCompositeDualBrush(
        primary: Bitmap,
        secondary: Bitmap,
        output: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modeCode: Int,
        applyRgb: Boolean,
    ): Boolean

    private fun IntArray.toRectOrNull(): Rect? = takeIf { size == 4 && it[0] >= 0 && it[1] >= 0 && it[2] > it[0] && it[3] > it[1] }
        ?.let { Rect(it[0], it[1], it[2], it[3]) }

    init {
        System.loadLibrary("inkloom_engine_v3")
    }
}
