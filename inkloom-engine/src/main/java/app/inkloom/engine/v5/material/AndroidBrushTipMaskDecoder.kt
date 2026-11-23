package app.inkloom.engine.v5.material

import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.ProcreateFiltering
import app.inkloom.document.TextureSampling
import android.graphics.Color
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

/** Import-time description of a repeating paper/grain texture. Shape and grain are intentionally
 * separate V5 resources: baking grain into a tip makes its phase and scale depend on decode size. */
data class GrainMaskRequest(
    val path: String,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val reversed: Boolean = false,
    val emphasized: Boolean = false,
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

    fun decodeGrain(request: GrainMaskRequest): BrushTipMask? {
        val bitmap = BrushTipTextureDecoder.decodeSampled(
            request.path,
            BrushTipTextureDecoder.MAX_RENDER_TEXTURE_DIMENSION,
        ) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val alpha = ByteArray(pixels.size) { index ->
                val pixel = pixels[index]
                val luminance = (Color.red(pixel) * .299f + Color.green(pixel) * .587f + Color.blue(pixel) * .114f) / 255f
                val effective = TextureSampling.effectiveLuminance(luminance, Color.alpha(pixel) / 255f)
                val coverage = TextureSampling.mask(
                    effective,
                    request.brightness,
                    request.contrast,
                    request.reversed,
                    request.emphasized,
                )
                (coverage * 255f + .5f).toInt().coerceIn(0, 255).toByte()
            }
            BrushTipMask(bitmap.width, bitmap.height, alpha)
        } finally {
            bitmap.recycle()
        }
    }
}

/** Small bounded cache of immutable V5 tip masks. No Android Bitmap survives admission. */
class BrushTipMaskCache(private val maximumBytes: Long = 24L * 1024L * 1024L) {
    private val values = object : LinkedHashMap<Any, BrushTipMask>(16, .75f, true) {}
    private var retainedBytes = 0L

    @Synchronized
    fun resolve(request: BrushTipMaskRequest): BrushTipMask? = resolve(request) {
        AndroidBrushTipMaskDecoder.decode(request)
    }

    @Synchronized
    fun resolveGrain(request: GrainMaskRequest): BrushTipMask? = resolve(request) {
        AndroidBrushTipMaskDecoder.decodeGrain(request)
    }

    private fun resolve(key: Any, decoder: () -> BrushTipMask?): BrushTipMask? {
        values[key]?.let { return it }
        val decoded = decoder() ?: return null
        if (decoded.byteSize > maximumBytes) return decoded
        while (retainedBytes + decoded.byteSize > maximumBytes && values.isNotEmpty()) {
            val eldest = values.entries.iterator().next()
            retainedBytes -= eldest.value.byteSize
            values.remove(eldest.key)
        }
        values[key] = decoded
        retainedBytes += decoded.byteSize
        return decoded
    }

    @Synchronized
    fun clear() {
        values.clear()
        retainedBytes = 0L
    }
}
