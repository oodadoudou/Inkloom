package app.inkloom.engine.v3.render

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.geometry.ViewportFrameId

/**
 * Immutable hand-off product shared by low-latency presentation and durable document commit.
 *
 * The packet intentionally contains document/page-space stroke data only. A presenter may apply
 * [transformFrameId]'s forward transform, but it must never reconstruct geometry from raw input.
 */
class StrokeRenderPacket private constructor(
    val sequence: Long,
    val transformFrameId: ViewportFrameId?,
    val strokes: List<InkStroke>,
    val documentBounds: StrokePacketBounds,
    val geometryFingerprint: Long,
    val materialFingerprint: Long,
) {
    val primaryStrokeId: String? = strokes.firstOrNull()?.id?.substringBefore("-sym-")

    companion object {
        fun create(
            sequence: Long,
            transformFrameId: ViewportFrameId?,
            strokes: List<InkStroke>,
        ): StrokeRenderPacket {
            val frozen = strokes.map { stroke ->
                stroke.copy(
                    points = stroke.points.toList(),
                    masks = stroke.masks.toList(),
                    tipAssetPaths = stroke.tipAssetPaths.toList(),
                    dualTipAssetPaths = stroke.dualTipAssetPaths.toList(),
                    colorMixPressureCurve = stroke.colorMixPressureCurve.toList(),
                    paintMixPressureCurve = stroke.paintMixPressureCurve.toList(),
                    sprayParticleSizePressureCurve = stroke.sprayParticleSizePressureCurve.toList(),
                    sprayDensityPressureCurve = stroke.sprayDensityPressureCurve.toList(),
                    sizePressureCurve = stroke.sizePressureCurve.toList(),
                    opacityPressureCurve = stroke.opacityPressureCurve.toList(),
                    flowPressureCurve = stroke.flowPressureCurve.toList(),
                    spacingPressureCurve = stroke.spacingPressureCurve.toList(),
                    thicknessPressureCurve = stroke.thicknessPressureCurve.toList(),
                    blurPressureCurve = stroke.blurPressureCurve.toList(),
                )
            }
            return build(sequence, transformFrameId, frozen)
        }

        /**
         * Allocation-bounded production factory. Callers must pass newly built immutable strokes;
         * unlike [create], this does not duplicate every point for every display frame.
         */
        internal fun createOwned(
            sequence: Long,
            transformFrameId: ViewportFrameId?,
            strokes: List<InkStroke>,
        ): StrokeRenderPacket = build(sequence, transformFrameId, strokes.toList())

        private fun build(
            sequence: Long,
            transformFrameId: ViewportFrameId?,
            frozen: List<InkStroke>,
        ): StrokeRenderPacket = StrokeRenderPacket(
                sequence = sequence,
                transformFrameId = transformFrameId,
                strokes = frozen,
                documentBounds = bounds(frozen),
                geometryFingerprint = fingerprintGeometry(frozen),
                materialFingerprint = fingerprintMaterial(frozen),
            )

        private fun bounds(strokes: List<InkStroke>): StrokePacketBounds {
            val points = strokes.asSequence().flatMap { it.points.asSequence() }.toList()
            if (points.isEmpty()) return StrokePacketBounds.EMPTY
            val padding = strokes.maxOfOrNull { stroke ->
                val spread = if (stroke.spray) stroke.spraySpread.coerceAtLeast(0f) else 0f
                val blur = stroke.blur.coerceAtLeast(0f) / 25f
                stroke.width.coerceAtLeast(1f) / REFERENCE_WIDTH * (1.5f + spread + blur)
            } ?: 0f
            return StrokePacketBounds(
                (points.minOf(StrokePoint::x) - padding).coerceIn(0f, 1f),
                (points.minOf(StrokePoint::y) - padding).coerceIn(0f, 1f),
                (points.maxOf(StrokePoint::x) + padding).coerceIn(0f, 1f),
                (points.maxOf(StrokePoint::y) + padding).coerceIn(0f, 1f),
            )
        }

        private fun fingerprintGeometry(strokes: List<InkStroke>): Long = fingerprint { add ->
            strokes.forEach { stroke ->
                add(stroke.id.hashCode())
                add(stroke.points.size)
                stroke.points.forEach { point -> addPoint(add, point) }
                add(stroke.masks.hashCode())
            }
        }

        private fun fingerprintMaterial(strokes: List<InkStroke>): Long = fingerprint { add ->
            strokes.forEach { stroke ->
                // Kotlin's generated data-class hash covers every current and future brush
                // semantic. Remove geometry, masks and identity first so this fingerprint answers
                // one question only: would the material program produce the same result?
                add(stroke.copy(id = "", points = emptyList(), masks = emptyList()).hashCode())
            }
        }

        private inline fun fingerprint(block: ((Int) -> Unit) -> Unit): Long {
            var hash = FNV_OFFSET_BASIS
            block { value ->
                repeat(Int.SIZE_BYTES) { byte ->
                    hash = hash xor ((value ushr (byte * 8)) and 0xFF).toLong()
                    hash *= FNV_PRIME
                }
            }
            return hash
        }

        private fun addPoint(add: (Int) -> Unit, point: StrokePoint) {
            add(java.lang.Float.floatToIntBits(point.x))
            add(java.lang.Float.floatToIntBits(point.y))
            add(java.lang.Float.floatToIntBits(point.pressure))
            add(java.lang.Float.floatToIntBits(point.tilt))
            add(java.lang.Float.floatToIntBits(point.orientation))
            add(java.lang.Float.floatToIntBits(point.pathDistance))
            add(point.timeMs.hashCode())
            add(point.sensorFlags)
        }

        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
        private const val REFERENCE_WIDTH = 1600f
    }
}

data class StrokePacketBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    companion object {
        val EMPTY = StrokePacketBounds(0f, 0f, 0f, 0f)
    }
}
