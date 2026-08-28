package app.inkloom.document

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

data class HandwritingRecognitionGroup(
    val strokeIds: List<String>,
    val strokes: List<InkStroke>,
    val bounds: PdfTextBounds,
)

object HandwritingIndex {
    /** V5 handwriting truth is the raster-layer generation, never serialized vector strokes. */
    fun fingerprint(page: InkPage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        page.layers.filter { it.contentKind == LayerContentKind.RASTER }.forEach { layer ->
            digest.update(layer.id.encodeToByteArray())
            layer.rasterTileSurface?.let { reference ->
                digest.update(reference.surfaceId.encodeToByteArray())
                digest.update(reference.revision.toString().encodeToByteArray())
                digest.update(reference.manifestAssetPath.encodeToByteArray())
            }
            layer.rasterAssetPath?.let { digest.update(it.encodeToByteArray()) }
            digest.update(0.toByte())
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

    fun fingerprint(strokes: List<InkStroke>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        strokes.forEach { stroke ->
            digest.update(stroke.id.encodeToByteArray())
            digest.update(0.toByte())
            stroke.points.forEach { point ->
                digest.update(point.x.toBits().toString().encodeToByteArray())
                digest.update(point.y.toBits().toString().encodeToByteArray())
                digest.update(point.timeMs.toString().encodeToByteArray())
            }
            digest.update(1.toByte())
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

    fun groups(strokes: List<InkStroke>): List<HandwritingRecognitionGroup> {
        val candidates = strokes.mapNotNull { stroke ->
            val bounds = bounds(stroke) ?: return@mapNotNull null
            StrokeBounds(stroke, bounds)
        }.sortedWith(compareBy<StrokeBounds>({ it.bounds.top }, { it.bounds.left }))
        if (candidates.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableList<StrokeBounds>>()
        candidates.forEach { candidate ->
            val center = (candidate.bounds.top + candidate.bounds.bottom) / 2f
            val target = lines.lastOrNull()?.takeIf { line ->
                val lineTop = line.minOf { it.bounds.top }
                val lineBottom = line.maxOf { it.bounds.bottom }
                val lineCenter = (lineTop + lineBottom) / 2f
                val tolerance = max(lineBottom - lineTop, candidate.bounds.bottom - candidate.bounds.top).coerceAtLeast(.018f)
                kotlin.math.abs(center - lineCenter) <= tolerance * .72f
            }
            if (target == null) lines += mutableListOf(candidate) else target += candidate
        }

        return lines.map { line ->
            val ordered = line.sortedWith(compareBy<StrokeBounds>({ it.bounds.left }, { firstTime(it.stroke) }))
            HandwritingRecognitionGroup(
                strokeIds = ordered.map { it.stroke.id },
                strokes = ordered.map(StrokeBounds::stroke),
                bounds = PdfTextBounds(
                    left = ordered.minOf { it.bounds.left },
                    top = ordered.minOf { it.bounds.top },
                    right = ordered.maxOf { it.bounds.right },
                    bottom = ordered.maxOf { it.bounds.bottom },
                ),
            )
        }
    }

    private fun bounds(stroke: InkStroke): PdfTextBounds? {
        if (stroke.points.isEmpty() || stroke.engineKind == BrushEngineKind.ERASE_MASK) return null
        var left = 1f
        var top = 1f
        var right = 0f
        var bottom = 0f
        stroke.points.forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        if (right - left < .0005f && bottom - top < .0005f) return null
        val padding = .004f
        return PdfTextBounds(
            (left - padding).coerceIn(0f, 1f),
            (top - padding).coerceIn(0f, 1f),
            (right + padding).coerceIn(0f, 1f),
            (bottom + padding).coerceIn(0f, 1f),
        )
    }

    private fun firstTime(stroke: InkStroke): Long = stroke.points.minOfOrNull(StrokePoint::timeMs) ?: 0L

    private data class StrokeBounds(val stroke: InkStroke, val bounds: PdfTextBounds)
}
