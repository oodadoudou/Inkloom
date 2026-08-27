package app.inkloom.engine.v5.material

import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.ProcreateFiltering
import java.util.LinkedHashMap

data class BrushTipMaskRequest(
    val path: String,
    val hardness: Float,
    val grainPath: String? = null,
    val grainDensity: Float = 0f,
    val grainScale: Float = 100f,
    val grainRotation: Float = 0f,
    val grainBrightness: Float = 0f,
    val grainContrast: Float = 0f,
    val grainReversed: Boolean = false,
    val grainEmphasized: Boolean = false,
    val grainCompositeMode: Int = CspTextureComposite.MULTIPLY,
    val grainFilteringMode: Int = ProcreateFiltering.NONE,
    val antiAliasMode: Int = CspAntiAlias.MEDIUM,
    val alphaThresholdEnabled: Boolean = false,
    val alphaThreshold: Float = 0f,
)

/** Decodes archive resources once, then discards Android bitmap ownership before live input. */
object AndroidBrushTipMaskDecoder {
    fun decode(request: BrushTipMaskRequest): BrushTipMask? {
        val bitmap = BrushTipTextureDecoder.decode(
            tipPath = request.path,
            hardness = request.hardness,
            grainPath = request.grainPath,
            grainDensity = request.grainDensity,
            grainScale = request.grainScale,
            grainRotation = request.grainRotation,
            grainBrightness = request.grainBrightness,
            grainContrast = request.grainContrast,
            grainReversed = request.grainReversed,
            grainEmphasized = request.grainEmphasized,
            grainCompositeMode = request.grainCompositeMode,
            grainFilteringMode = request.grainFilteringMode,
            antiAliasMode = request.antiAliasMode,
            alphaThresholdEnabled = request.alphaThresholdEnabled,
            alphaThreshold = request.alphaThreshold,
        ) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            BrushTipMask(
                bitmap.width,
                bitmap.height,
                ByteArray(pixels.size) { index -> (pixels[index] ushr 24).toByte() },
            )
        } finally {
            bitmap.recycle()
        }
    }
}

/** Small bounded cache of immutable V5 tip masks. No Android Bitmap survives admission. */
class BrushTipMaskCache(private val maximumBytes: Long = 24L * 1024L * 1024L) {
    private val values = object : LinkedHashMap<BrushTipMaskRequest, BrushTipMask>(16, .75f, true) {}
    private var retainedBytes = 0L

    @Synchronized
    fun resolve(request: BrushTipMaskRequest): BrushTipMask? {
        values[request]?.let { return it }
        val decoded = AndroidBrushTipMaskDecoder.decode(request) ?: return null
        if (decoded.byteSize > maximumBytes) return decoded
        while (retainedBytes + decoded.byteSize > maximumBytes && values.isNotEmpty()) {
            val eldest = values.entries.iterator().next()
            retainedBytes -= eldest.value.byteSize
            values.remove(eldest.key)
        }
        values[request] = decoded
        retainedBytes += decoded.byteSize
        return decoded
    }

    @Synchronized
    fun clear() {
        values.clear()
        retainedBytes = 0L
    }
}

