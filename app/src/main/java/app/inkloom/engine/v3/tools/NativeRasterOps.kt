package app.inkloom.engine.v3.tools

import android.graphics.Bitmap

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

    init {
        System.loadLibrary("inkloom_engine_v3")
    }
}
