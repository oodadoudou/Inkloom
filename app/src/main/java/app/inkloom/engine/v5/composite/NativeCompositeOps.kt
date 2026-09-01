package app.inkloom.engine.v5.composite

/** Android acceleration for bounded separable blend modes. JVM tests use the Kotlin reference. */
internal object NativeCompositeOps {
    private val available = runCatching { System.loadLibrary("inkloom_engine_v5") }.isSuccess
    val isAvailable: Boolean get() = available

    fun compositeRgba8(
        source: IntArray,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mode: PixelBlendMode,
        opacity: Float,
    ): Boolean = available && nativeCompositeRgba8(
        source,
        destination,
        stride,
        left,
        top,
        right,
        bottom,
        mode.ordinal,
        opacity,
    )

    fun compositeF16(
        source: LongArray,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mode: PixelBlendMode,
        opacity: Float,
    ): Boolean = available && nativeCompositeF16(
        source,
        destination,
        stride,
        left,
        top,
        right,
        bottom,
        mode.ordinal,
        opacity,
    )

    fun compositeRgba8Layers(
        sources: IntArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean = available && nativeCompositeRgba8Layers(
        sources,
        sourceLayerStride,
        layerCount,
        destination,
        stride,
        left,
        top,
        right,
        bottom,
        modes,
        opacities,
    )

    fun compositeF16Layers(
        sources: LongArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean = available && nativeCompositeF16Layers(
        sources,
        sourceLayerStride,
        layerCount,
        destination,
        stride,
        left,
        top,
        right,
        bottom,
        modes,
        opacities,
    )

    fun compositeRgba8LayersWithCoverage(
        sources: IntArray,
        coverage: ByteArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean = available && nativeCompositeRgba8LayersWithCoverage(
        sources, coverage, sourceLayerStride, layerCount, destination, stride,
        left, top, right, bottom, modes, opacities,
    )

    fun compositeF16LayersWithCoverage(
        sources: LongArray,
        coverage: ByteArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean = available && nativeCompositeF16LayersWithCoverage(
        sources, coverage, sourceLayerStride, layerCount, destination, stride,
        left, top, right, bottom, modes, opacities,
    )

    fun multiplyCoverageByRgba8Alpha(
        coverage: ByteArray,
        coverageOffset: Int,
        pixels: IntArray,
        stride: Int,
        width: Int,
        height: Int,
        opacityByte: Int,
    ): Boolean = available && nativeMultiplyCoverageByRgba8Alpha(
        coverage,
        coverageOffset,
        pixels,
        stride,
        width,
        height,
        opacityByte,
    )

    private external fun nativeCompositeRgba8(
        source: IntArray,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mode: Int,
        opacity: Float,
    ): Boolean

    private external fun nativeCompositeF16(
        source: LongArray,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mode: Int,
        opacity: Float,
    ): Boolean

    private external fun nativeCompositeRgba8Layers(
        sources: IntArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean

    private external fun nativeCompositeF16Layers(
        sources: LongArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean

    private external fun nativeCompositeRgba8LayersWithCoverage(
        sources: IntArray,
        coverage: ByteArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean

    private external fun nativeCompositeF16LayersWithCoverage(
        sources: LongArray,
        coverage: ByteArray,
        sourceLayerStride: Int,
        layerCount: Int,
        destination: LongArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        modes: IntArray,
        opacities: FloatArray,
    ): Boolean
    private external fun nativeMultiplyCoverageByRgba8Alpha(
        coverage: ByteArray,
        coverageOffset: Int,
        pixels: IntArray,
        stride: Int,
        width: Int,
        height: Int,
        opacityByte: Int,
    ): Boolean
}
