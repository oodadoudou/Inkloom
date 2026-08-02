package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class StrokeClipboardPayload(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val version: Int = StrokeClipboardCodec.VERSION,
    val strokes: List<InkStroke>,
)

object StrokeClipboardCodec {
    const val MIME_TYPE = "application/vnd.inkloom.strokes+json"
    const val VERSION = 1
    private const val MAX_ENCODED_CHARS = 2_000_000
    private const val MAX_STROKES = 500
    private const val MAX_POINTS = 100_000
    private const val MAX_MASKS = 100_000
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(strokes: List<InkStroke>): String? {
        if (!valid(strokes)) return null
        return runCatching { json.encodeToString(StrokeClipboardPayload(strokes = strokes)) }.getOrNull()?.takeIf { it.length <= MAX_ENCODED_CHARS }
    }

    fun decode(text: CharSequence?): StrokeClipboardPayload? {
        if (text == null || text.length !in 1..MAX_ENCODED_CHARS) return null
        return runCatching { json.decodeFromString<StrokeClipboardPayload>(text.toString()) }.getOrNull()
            ?.takeIf { it.version == VERSION && valid(it.strokes) }
    }

    fun materialize(
        payload: StrokeClipboardPayload,
        targetLayerId: String,
        desiredOffset: Float = .03f,
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): List<InkStroke> {
        if (!valid(payload.strokes)) return emptyList()
        val points = payload.strokes.flatMap(InkStroke::points)
        val minX = points.minOf(StrokePoint::x)
        val maxX = points.maxOf(StrokePoint::x)
        val minY = points.minOf(StrokePoint::y)
        val maxY = points.maxOf(StrokePoint::y)
        val dx = desiredOffset.coerceIn(-minX, 1f - maxX)
        val dy = desiredOffset.coerceIn(-minY, 1f - maxY)
        return payload.strokes.map { stroke ->
            stroke.copy(
                id = nextId(),
                layerId = targetLayerId,
                points = stroke.points.map { point -> point.copy(x = (point.x + dx).coerceIn(0f, 1f), y = (point.y + dy).coerceIn(0f, 1f)) },
                masks = stroke.masks.map { mask -> mask.copy(x = (mask.x + dx).coerceIn(0f, 1f), y = (mask.y + dy).coerceIn(0f, 1f)) },
            )
        }
    }

    private fun valid(strokes: List<InkStroke>): Boolean {
        if (strokes.isEmpty() || strokes.size > MAX_STROKES || strokes.sumOf { it.points.size } > MAX_POINTS || strokes.sumOf { it.masks.size } > MAX_MASKS) return false
        return strokes.all { stroke ->
            stroke.points.isNotEmpty() && stroke.width.isFinite() && stroke.width in .01f..10_000f && stroke.opacity.isFinite() && stroke.opacity in 0f..1f &&
                stroke.points.all { point -> point.x.isFinite() && point.y.isFinite() && point.pressure.isFinite() && point.x in 0f..1f && point.y in 0f..1f && point.pressure in 0f..1f } &&
                stroke.masks.all { mask -> mask.x.isFinite() && mask.y.isFinite() && mask.radiusX.isFinite() && mask.radiusY.isFinite() && mask.rotationDegrees.isFinite() && mask.hardness.isFinite() && mask.opacity.isFinite() && mask.x in 0f..1f && mask.y in 0f..1f && mask.radiusX in 0f..10f && mask.radiusY in 0f..10f && mask.hardness in 0f..1f && mask.opacity in 0f..1f }
        }
    }
}
