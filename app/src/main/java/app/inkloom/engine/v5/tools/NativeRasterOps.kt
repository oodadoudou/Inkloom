package app.inkloom.engine.v5.tools

import android.graphics.Bitmap
import android.graphics.Rect

/** Coarse-grained JNI entry points for bounded raster work that Android's software Canvas cannot
 * execute with RuntimeShader. Pixel buffers never cross into Kotlin. */
internal object NativeRasterOps {
    fun affineSelectionTile(source: Any, sourceAlpha: ByteArray, sourceLeft: Int, sourceTop: Int, sourceWidth: Int, sourceHeight: Int,
                            destination: Any, destinationAlpha: ByteArray, tileLeft: Int, tileTop: Int, stride: Int, width: Int, height: Int,
                            inverse: FloatArray, interpolation: Int, highPrecision: Boolean): Boolean = nativeLibraryLoaded &&
        nativeAffineSelectionTile(source, sourceAlpha, sourceLeft, sourceTop, sourceWidth, sourceHeight, destination, destinationAlpha,
            tileLeft, tileTop, stride, width, height, inverse, interpolation, highPrecision)
    private external fun nativeAffineSelectionTile(source: Any, sourceAlpha: ByteArray, sourceLeft: Int, sourceTop: Int, sourceWidth: Int, sourceHeight: Int,
        destination: Any, destinationAlpha: ByteArray, tileLeft: Int, tileTop: Int, stride: Int, width: Int, height: Int,
        inverse: FloatArray, interpolation: Int, highPrecision: Boolean): Boolean

    /** Applies a constant separable filter to one bounded alpha ROI. -1 selects the JVM oracle. */
    fun affineSelectionAlpha(input: ByteArray, sourceLeft: Int, sourceTop: Int, sourceWidth: Int, sourceHeight: Int,
                             output: ByteArray, left: Int, top: Int, width: Int, height: Int, inverse: FloatArray, interpolation: Int): Int =
        if (nativeLibraryLoaded) nativeAffineSelectionAlpha(input, sourceLeft, sourceTop, sourceWidth, sourceHeight,
            output, left, top, width, height, inverse, interpolation) else -1

    private external fun nativeAffineSelectionAlpha(input: ByteArray, sourceLeft: Int, sourceTop: Int, sourceWidth: Int, sourceHeight: Int,
        output: ByteArray, left: Int, top: Int, width: Int, height: Int, inverse: FloatArray, interpolation: Int): Int

    fun translatedSelectionAlpha(input: ByteArray, stride: Int, output: ByteArray, width: Int, height: Int,
                                 weightsX: FloatArray, weightsY: FloatArray): Int =
        if (nativeLibraryLoaded) nativeTranslatedSelectionAlpha(input, stride, output, width, height, weightsX, weightsY) else -1

    private external fun nativeTranslatedSelectionAlpha(input: ByteArray, stride: Int, output: ByteArray,
        width: Int, height: Int, weightsX: FloatArray, weightsY: FloatArray): Int

    /**
     * Composites one dirty rectangle from an immutable premultiplied source tile into a mutable
     * premultiplied destination tile. Returning false lets host-side JVM tests use the exact
     * Kotlin reference implementation when the Android native library is unavailable.
     */
    fun compositePremultipliedTile(
        destination: IntArray,
        source: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blendMode: Int,
    ): Boolean = nativeLibraryLoaded && nativeCompositePremultipliedTile(
        destination,
        source,
        stride,
        left,
        top,
        right,
        bottom,
        blendMode,
    )

    /**
     * Turns an owned contribution tile into a complete immutable document tile in place.
     * The low bits contain the alpha pixel count and [MATERIALIZE_CHANGED_FLAG] reports whether
     * the result differs from the backdrop. -1 means the native path is unavailable.
     */
    fun materializePremultipliedTile(
        contribution: IntArray,
        backdrop: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blendMode: Int,
    ): Int = if (nativeLibraryLoaded) nativeMaterializePremultipliedTile(
        contribution, backdrop, stride, left, top, right, bottom, blendMode,
    ) else -1

    /** -1 means unavailable/invalid, 0 means executed without a changed pixel, 1 means changed. */
    fun depositRoundWet(
        destination: IntArray,
        stride: Int,
        tileLeft: Int,
        tileTop: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float,
        inverseEdge: Float,
        baseDeposition: Float,
        transferMode: Int,
        reservoirAlpha: Float,
        reservoirRed: Float,
        reservoirGreen: Float,
        reservoirBlue: Float,
        opacityLimit: Float = 1f,
    ): Int = if (nativeLibraryLoaded) nativeDepositRoundWet(
        destination,
        stride,
        tileLeft,
        tileTop,
        left,
        top,
        right,
        bottom,
        centerX,
        centerY,
        outerRadius,
        innerRadius,
        inverseEdge,
        baseDeposition,
        transferMode,
        reservoirAlpha,
        reservoirRed,
        reservoirGreen,
        reservoirBlue, opacityLimit,
    ) else -1

    /** Textured wet deposition; -1 means fall back to the Kotlin reference implementation. */
    fun depositTexturedWet(
        destination: IntArray,
        stride: Int,
        tileLeft: Int,
        tileTop: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        cosine: Float,
        sine: Float,
        outerRadius: Float,
        innerRadius: Float,
        inverseEdge: Float,
        tip: ByteArray?,
        tipWidth: Int,
        tipHeight: Int,
        grain: ByteArray?,
        grainWidth: Int,
        grainHeight: Int,
        grainDensity: Float,
        grainCosine: Float,
        grainSine: Float,
        grainWavelength: Float,
        grainMovement: Float,
        grainPhaseX: Float,
        grainPhaseY: Float,
        baseDeposition: Float,
        transferMode: Int,
        reservoirAlpha: Float,
        reservoirRed: Float,
        reservoirGreen: Float,
        reservoirBlue: Float,
        opacityLimit: Float = 1f,
    ): Int = if (nativeLibraryLoaded) nativeDepositTexturedWet(
        destination, stride, tileLeft, tileTop, left, top, right, bottom,
        centerX, centerY, radiusX, radiusY, cosine, sine,
        outerRadius, innerRadius, inverseEdge,
        tip, tipWidth, tipHeight, grain, grainWidth, grainHeight,
        grainDensity, grainCosine, grainSine, grainWavelength, grainMovement,
        grainPhaseX, grainPhaseY, baseDeposition, transferMode,
        reservoirAlpha, reservoirRed, reservoirGreen, reservoirBlue, opacityLimit,
    ) else -1

    /** Exact bounded dual-stream alpha accumulation; false leaves the destination untouched. */
    fun accumulateStampCoverage(
        destination: ByteArray, stride: Int, tileLeft: Int, tileTop: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
        cosine: Float, sine: Float, depositedAlpha: Float,
        tip: ByteArray, tipWidth: Int, tipHeight: Int,
        grain: ByteArray?, grainWidth: Int, grainHeight: Int,
        grainDensity: Float, grainCosine: Float, grainSine: Float,
        grainWavelength: Float, grainMovement: Float, grainPhaseX: Float, grainPhaseY: Float,
    ): Boolean = nativeLibraryLoaded && nativeAccumulateStampCoverage(
        destination, stride, tileLeft, tileTop, left, top, right, bottom,
        centerX, centerY, radiusX, radiusY, cosine, sine, depositedAlpha,
        tip, tipWidth, tipHeight, grain, grainWidth, grainHeight,
        grainDensity, grainCosine, grainSine, grainWavelength, grainMovement, grainPhaseX, grainPhaseY,
    )

    private external fun nativeAccumulateStampCoverage(
        destination: ByteArray, stride: Int, tileLeft: Int, tileTop: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
        cosine: Float, sine: Float, depositedAlpha: Float,
        tip: ByteArray, tipWidth: Int, tipHeight: Int,
        grain: ByteArray?, grainWidth: Int, grainHeight: Int,
        grainDensity: Float, grainCosine: Float, grainSine: Float,
        grainWavelength: Float, grainMovement: Float, grainPhaseX: Float, grainPhaseY: Float,
    ): Boolean

    /** Exact bounded uniform-glaze source-over deposition; false leaves the destination untouched. */
    fun depositUniformStamp(
        destination: IntArray, stride: Int, tileLeft: Int, tileTop: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
        cosine: Float, sine: Float, depositedAlpha: Float,
        tip: ByteArray, tipWidth: Int, tipHeight: Int,
        grain: ByteArray?, grainWidth: Int, grainHeight: Int,
        grainDensity: Float, grainCosine: Float, grainSine: Float,
        grainWavelength: Float, grainMovement: Float, grainPhaseX: Float, grainPhaseY: Float, color: Int,
    ): Boolean = nativeLibraryLoaded && nativeDepositUniformStamp(
        destination, stride, tileLeft, tileTop, left, top, right, bottom,
        centerX, centerY, radiusX, radiusY, cosine, sine, depositedAlpha,
        tip, tipWidth, tipHeight, grain, grainWidth, grainHeight,
        grainDensity, grainCosine, grainSine, grainWavelength, grainMovement, grainPhaseX, grainPhaseY, color,
    )

    private external fun nativeDepositUniformStamp(
        destination: IntArray, stride: Int, tileLeft: Int, tileTop: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
        cosine: Float, sine: Float, depositedAlpha: Float,
        tip: ByteArray, tipWidth: Int, tipHeight: Int,
        grain: ByteArray?, grainWidth: Int, grainHeight: Int,
        grainDensity: Float, grainCosine: Float, grainSine: Float,
        grainWavelength: Float, grainMovement: Float, grainPhaseX: Float, grainPhaseY: Float, color: Int,
    ): Boolean

    fun compositeDualCoverage(
        destination: IntArray, primary: ByteArray?, secondary: ByteArray?, stride: Int,
        left: Int, top: Int, right: Int, bottom: Int, mode: Int, color: Int, erase: Boolean,
    ): Boolean = nativeLibraryLoaded && nativeCompositeDualCoverage(
        destination, primary, secondary, stride, left, top, right, bottom, mode, color, erase,
    )

    private external fun nativeCompositeDualCoverage(
        destination: IntArray, primary: ByteArray?, secondary: ByteArray?, stride: Int,
        left: Int, top: Int, right: Int, bottom: Int, mode: Int, color: Int, erase: Boolean,
    ): Boolean

    /** Bounded rectangle copy without interpreted Kotlin work for every scanline. */
    fun copyPixelRows(
        source: IntArray, destination: IntArray, sourceOffset: Int, destinationOffset: Int,
        width: Int, height: Int, sourceStride: Int, destinationStride: Int,
    ): Boolean = nativeLibraryLoaded && nativeCopyPixelRows(
        source, destination, sourceOffset, destinationOffset, width, height, sourceStride, destinationStride,
    )

    private external fun nativeCopyPixelRows(
        source: IntArray, destination: IntArray, sourceOffset: Int, destinationOffset: Int,
        width: Int, height: Int, sourceStride: Int, destinationStride: Int,
    ): Boolean

    /** Same quantization as unpremultiply + Bitmap.setPixels, in one bounded native write. */
    fun writePremultipliedBitmap(bitmap: Bitmap, pixels: IntArray): Boolean =
        nativeLibraryLoaded && bitmap.isMutable && nativeWritePremultipliedBitmap(bitmap, pixels)

    private external fun nativeWritePremultipliedBitmap(bitmap: Bitmap, pixels: IntArray): Boolean

    fun unpremultiplyInPlace(pixels: IntArray): Boolean =
        nativeLibraryLoaded && nativeUnpremultiplyInPlace(pixels)

    /** RGBA footprint averages; false uses the host reference implementation. */
    fun samplePigmentBuckets(pixels: IntArray, width: Int, height: Int, columns: Int, rows: Int, output: FloatArray): Boolean =
        nativeLibraryLoaded && nativeSamplePigmentBuckets(pixels, width, height, columns, rows, output)

    private external fun nativeSamplePigmentBuckets(pixels: IntArray, width: Int, height: Int, columns: Int, rows: Int, output: FloatArray): Boolean

    fun premultiplyInPlace(pixels: IntArray): Boolean =
        nativeLibraryLoaded && nativePremultiplyInPlace(pixels)

    /** Returns -1 when native execution is unavailable. */
    fun nonTransparentPixelCount(pixels: IntArray): Int =
        if (nativeLibraryLoaded) nativeNonTransparentPixelCount(pixels) else -1

    /** Stable architecture-independent A,R,G,B FNV-1a hash, or null off Android. */
    fun hashPremultipliedPixels(pixels: IntArray): Long? =
        if (nativeLibraryLoaded) nativeHashPremultipliedPixels(pixels) else null

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

    fun nonEmptyAlphaTiles(source: Bitmap, tileSize: Int): IntArray =
        nativeNonEmptyAlphaTiles(source, tileSize)

    fun invertAlpha(source: Bitmap, destination: Bitmap): Boolean = nativeInvertAlpha(source, destination)

    fun boundaryAlpha(source: Bitmap, destination: Bitmap): Boolean = nativeBoundaryAlpha(source, destination)

    fun morphAlpha(source: Bitmap, destination: Bitmap, radius: Int, dilate: Boolean): Boolean =
        nativeMorphAlpha(source, destination, radius, dilate)

    fun applySelectionAlpha(
        target: Bitmap,
        selection: Bitmap,
        globalLeft: Int,
        globalTop: Int,
        contentWidth: Int,
        contentHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): Boolean = nativeApplySelectionAlpha(
        target,
        selection,
        globalLeft,
        globalTop,
        contentWidth,
        contentHeight,
        surfaceWidth,
        surfaceHeight,
    )

    fun floodFill(
        source: Bitmap,
        destination: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int,
        areaScalingPixels: Int,
        targetColor: Int?,
        expansionShape: Int,
    ): Rect? = nativeFloodFill(
        source,
        destination,
        startX,
        startY,
        tolerance,
        closeGapPixels,
        areaScalingPixels,
        targetColor != null,
        targetColor ?: 0,
        expansionShape,
    ).toRectOrNull()

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
    private external fun nativeNonEmptyAlphaTiles(source: Bitmap, tileSize: Int): IntArray
    private external fun nativeInvertAlpha(source: Bitmap, destination: Bitmap): Boolean
    private external fun nativeBoundaryAlpha(source: Bitmap, destination: Bitmap): Boolean
    private external fun nativeMorphAlpha(source: Bitmap, destination: Bitmap, radius: Int, dilate: Boolean): Boolean
    private external fun nativeApplySelectionAlpha(
        target: Bitmap,
        selection: Bitmap,
        globalLeft: Int,
        globalTop: Int,
        contentWidth: Int,
        contentHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): Boolean
    private external fun nativeFloodFill(
        source: Bitmap,
        destination: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int,
        areaScalingPixels: Int,
        hasTargetColor: Boolean,
        targetColor: Int,
        expansionShape: Int,
    ): IntArray
    private external fun nativeCompositePremultipliedTile(
        destination: IntArray,
        source: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blendMode: Int,
    ): Boolean
    private external fun nativeDepositRoundWet(
        destination: IntArray,
        stride: Int,
        tileLeft: Int,
        tileTop: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float,
        inverseEdge: Float,
        baseDeposition: Float,
        transferMode: Int,
        reservoirAlpha: Float,
        reservoirRed: Float,
        reservoirGreen: Float,
        reservoirBlue: Float,
        opacityLimit: Float,
    ): Int
    private external fun nativeDepositTexturedWet(
        destination: IntArray,
        stride: Int,
        tileLeft: Int,
        tileTop: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        cosine: Float,
        sine: Float,
        outerRadius: Float,
        innerRadius: Float,
        inverseEdge: Float,
        tip: ByteArray?,
        tipWidth: Int,
        tipHeight: Int,
        grain: ByteArray?,
        grainWidth: Int,
        grainHeight: Int,
        grainDensity: Float,
        grainCosine: Float,
        grainSine: Float,
        grainWavelength: Float,
        grainMovement: Float,
        grainPhaseX: Float,
        grainPhaseY: Float,
        baseDeposition: Float,
        transferMode: Int,
        reservoirAlpha: Float,
        reservoirRed: Float,
        reservoirGreen: Float,
        reservoirBlue: Float,
        opacityLimit: Float,
    ): Int
    private external fun nativeMaterializePremultipliedTile(
        contribution: IntArray,
        backdrop: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blendMode: Int,
    ): Int
    private external fun nativeUnpremultiplyInPlace(pixels: IntArray): Boolean
    private external fun nativePremultiplyInPlace(pixels: IntArray): Boolean
    private external fun nativeNonTransparentPixelCount(pixels: IntArray): Int
    private external fun nativeHashPremultipliedPixels(pixels: IntArray): Long

    const val MATERIALIZE_CHANGED_FLAG: Int = 1 shl 30
    private fun IntArray.toRectOrNull(): Rect? = takeIf { size == 4 && it[0] >= 0 && it[1] >= 0 && it[2] > it[0] && it[3] > it[1] }
        ?.let { Rect(it[0], it[1], it[2], it[3]) }

    private val nativeLibraryLoaded = runCatching {
        System.loadLibrary("inkloom_engine_v5")
    }.isSuccess
}
