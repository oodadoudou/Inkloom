package app.inkloom.engine.v3.brush

import android.graphics.Bitmap
import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspTextureMaterial
import app.inkloom.document.ProcreateFiltering
import app.inkloom.engine.v5.android.PixelMemoryPressure
import app.inkloom.engine.v5.android.PixelMemoryPressureCoordinator
import app.inkloom.engine.v5.android.PixelMemoryTrimResult
import app.inkloom.engine.v5.android.PixelMemoryTrimmable
import java.util.LinkedHashMap

/** Immutable description of one decoded render tip. Format importers are deliberately absent:
 * every supported brush format must resolve to this request before it reaches the renderer. */
internal data class BrushTipRequest(
    val path: String,
    val hardness: Float,
    val grainPath: String?,
    val grainDensity: Float,
    val grainScale: Float = 100f,
    val grainRotation: Float = 0f,
    val grainBrightness: Float = 0f,
    val grainContrast: Float = 0f,
    val grainReversed: Boolean = false,
    val grainEmphasized: Boolean = false,
    val grainOffsetX: Float = 0f,
    val grainOffsetY: Float = 0f,
    val grainCompositeMode: Int = CspTextureComposite.MULTIPLY,
    val grainFilteringMode: Int = ProcreateFiltering.NONE,
    val antiAliasMode: Int = CspAntiAlias.MEDIUM,
    val alphaThresholdEnabled: Boolean = false,
    val alphaThreshold: Float = 0f,
    val preserveRgb: Boolean = false,
) {
    val key: BrushTipKey = BrushTipKey(
        path = path,
        hardnessBucket = (hardness.coerceIn(0f, 1f) * 20).toInt(),
        grainPath = grainPath,
        grainDensityBucket = (grainDensity.coerceIn(0f, 1f) * 20).toInt(),
        grainScaleBucket = grainScale.toInt(),
        grainRotationBucket = grainRotation.toInt(),
        grainBrightnessBucket = grainBrightness.toInt(),
        grainContrastBucket = grainContrast.toInt(),
        grainReversed = grainReversed,
        grainEmphasized = grainEmphasized,
        grainOffsetXBits = grainOffsetX.toBits(),
        grainOffsetYBits = grainOffsetY.toBits(),
        grainCompositeMode = grainCompositeMode,
        grainFilteringMode = ProcreateFiltering.normalize(grainFilteringMode),
        antiAliasMode = CspAntiAlias.normalize(antiAliasMode),
        alphaThresholdEnabled = alphaThresholdEnabled,
        alphaThresholdBucket = (alphaThreshold.coerceIn(0f, 1f) * 255f).toInt(),
        preserveRgb = preserveRgb,
    )

    fun decode(): Bitmap? = BrushTipTextureDecoder.decode(
        path,
        hardness,
        grainPath,
        grainDensity,
        grainScale,
        grainRotation,
        grainBrightness,
        grainContrast,
        grainReversed,
        grainEmphasized,
        grainOffsetX,
        grainOffsetY,
        grainCompositeMode,
        key.grainFilteringMode,
        key.antiAliasMode,
        alphaThresholdEnabled,
        alphaThreshold,
        preserveRgb,
    )
}

internal data class BrushTipKey(
    val path: String,
    val hardnessBucket: Int,
    val grainPath: String?,
    val grainDensityBucket: Int,
    val grainScaleBucket: Int,
    val grainRotationBucket: Int,
    val grainBrightnessBucket: Int,
    val grainContrastBucket: Int,
    val grainReversed: Boolean,
    val grainEmphasized: Boolean,
    val grainOffsetXBits: Int,
    val grainOffsetYBits: Int,
    val grainCompositeMode: Int,
    val grainFilteringMode: Int,
    val antiAliasMode: Int,
    val alphaThresholdEnabled: Boolean,
    val alphaThresholdBucket: Int,
    val preserveRgb: Boolean,
)

/** View-independent owner for decoded immutable brush resources.
 *
 * Android display lists may retain bitmap references after recording. The cache therefore owns
 * every admitted bitmap until eviction or [close], and callers must never recycle returned tips.
 * All mutation methods are synchronized because decoding completes off the UI thread while scene
 * recording and cursor queries run on the UI/render threads.
 */
internal class BrushResourceCache(
    private val maxTipEntries: Int = DEFAULT_MAX_TIP_ENTRIES,
    private val maxPendingLoads: Int = DEFAULT_MAX_PENDING_LOADS,
    private val maxGrainMaterials: Int = DEFAULT_MAX_GRAIN_MATERIALS,
) : AutoCloseable, PixelMemoryTrimmable {
    private val tips = object : LinkedHashMap<BrushTipKey, Bitmap>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BrushTipKey, Bitmap>): Boolean {
            val remove = size > maxTipEntries
            if (remove && !eldest.value.isRecycled) eldest.value.recycle()
            return remove
        }
    }
    private val pending = mutableSetOf<BrushTipKey>()
    private val grainMaterials = object : LinkedHashMap<String, CspTextureMaterial>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CspTextureMaterial>): Boolean =
            size > maxGrainMaterials
    }
    private val proceduralTips = mutableMapOf<String, Bitmap>()
    private var closed = false
    private val memoryRegistration = PixelMemoryPressureCoordinator.register(this)

    override val memoryTrimPriority: Int get() = 100

    @Synchronized
    fun tip(request: BrushTipRequest): Bitmap? = tips[request.key]?.takeUnless(Bitmap::isRecycled)

    @Synchronized
    fun tipForPath(path: String): Bitmap? = tips.entries.firstOrNull { (key, bitmap) ->
        key.path == path && !bitmap.isRecycled
    }?.value

    @Synchronized
    fun putTip(request: BrushTipRequest, bitmap: Bitmap): Boolean {
        if (closed || bitmap.isRecycled) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return false
        }
        val previous = tips.put(request.key, bitmap)
        if (previous !== bitmap && previous != null && !previous.isRecycled) previous.recycle()
        return true
    }

    fun decodeNow(request: BrushTipRequest): Bitmap? {
        tip(request)?.let { return it }
        val decoded = request.decode() ?: return null
        return if (putTip(request, decoded)) decoded else null
    }

    @Synchronized
    fun beginLoad(request: BrushTipRequest): Boolean {
        if (closed || tips.containsKey(request.key) || pending.size >= maxPendingLoads) return false
        return pending.add(request.key)
    }

    @Synchronized
    fun completeLoad(request: BrushTipRequest, decoded: Bitmap?): Boolean {
        pending.remove(request.key)
        if (decoded == null) return false
        return putTip(request, decoded)
    }

    @Synchronized
    fun isLoading(request: BrushTipRequest): Boolean = request.key in pending

    @Synchronized
    fun grainMaterial(path: String): CspTextureMaterial? {
        grainMaterials[path]?.let { return it }
        if (closed) return null
        return CspTextureMaterial.decode(path)?.also { grainMaterials[path] = it }
    }

    @Synchronized
    fun proceduralTip(key: String, create: () -> Bitmap?): Bitmap? {
        proceduralTips[key]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        if (closed) return null
        return create()?.also { proceduralTips[key] = it }
    }

    @Synchronized
    internal fun countsForTests(): IntArray = intArrayOf(tips.size, pending.size, grainMaterials.size, proceduralTips.size)

    @Synchronized
    override fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult {
        if (closed) return PixelMemoryTrimResult()
        val before = tips.values.sumOf { it.allocationByteCount.toLong() } +
            proceduralTips.values.sumOf { it.allocationByteCount.toLong() }
        tips.values.forEach { if (!it.isRecycled) it.recycle() }
        tips.clear()
        grainMaterials.clear()
        proceduralTips.values.forEach { if (!it.isRecycled) it.recycle() }
        proceduralTips.clear()
        return PixelMemoryTrimResult(releasedCpuBytes = before)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        memoryRegistration.close()
        tips.values.forEach { if (!it.isRecycled) it.recycle() }
        tips.clear()
        pending.clear()
        grainMaterials.clear()
        proceduralTips.values.forEach { if (!it.isRecycled) it.recycle() }
        proceduralTips.clear()
    }

    companion object {
        const val DEFAULT_MAX_TIP_ENTRIES = 64
        const val DEFAULT_MAX_PENDING_LOADS = 12
        const val DEFAULT_MAX_GRAIN_MATERIALS = 8
    }
}
