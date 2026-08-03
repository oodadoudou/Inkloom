package app.inkloom.importer

import app.inkloom.document.BrushCurvePoint
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SutEffector(
    val formatFlags: Int,
    val sourceFlags: Int,
    val pressureMinimum: Float,
    val tiltMinimum: Float,
    val velocityMinimum: Float,
    val randomMinimum: Float,
    val randomMinimumSigned: Float,
    val velocityScale: Float,
    val curves: List<List<BrushCurvePoint>>,
) {
    val pressureEnabled: Boolean get() = sourceFlags and PRESSURE != 0
    val tiltEnabled: Boolean get() = sourceFlags and TILT != 0
    val velocityEnabled: Boolean get() = sourceFlags and VELOCITY != 0
    val randomEnabled: Boolean get() = sourceFlags and RANDOM != 0
    val pressureCurve: List<BrushCurvePoint> get() = curves.getOrNull(0).orEmpty()
    val velocityCurve: List<BrushCurvePoint> get() = curves.getOrNull(1).orEmpty()

    companion object {
        const val PRESSURE = 0x10
        const val TILT = 0x20
        const val VELOCITY = 0x40
        const val RANDOM = 0x80
    }
}

object SutEffectorDecoder {
    private const val HEADER_BYTES = 44

    fun decode(blob: ByteArray?): SutEffector? {
        if (blob == null || blob.size < HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != HEADER_BYTES) return null
        val formatFlags = buffer.int
        val sourceFlags = buffer.int
        val pressureMinimum = buffer.percent()
        val tiltMinimum = buffer.percent()
        val velocityMinimum = buffer.percent()
        val randomMinimumSigned = (buffer.int / 100f).coerceIn(-1f, 1f)
        val randomMinimum = randomMinimumSigned.coerceAtLeast(0f)
        buffer.int // reserved
        buffer.int // pressure curve chunk length
        buffer.int // velocity curve chunk length
        val velocityScale = (buffer.int / 100f).coerceIn(0f, 10f)
        buffer.position(HEADER_BYTES)
        val curves = buildList {
            while (buffer.remaining() >= 12) {
                val marker = buffer.int
                val count = buffer.int
                val stride = buffer.int
                if (marker != 12 || count !in 1..64 || stride != 16 || buffer.remaining() < count * stride) break
                val points = buildList {
                    repeat(count) {
                        val x = buffer.double
                        val y = buffer.double
                        if (x.isFinite() && y.isFinite()) add(BrushCurvePoint(x.toFloat().coerceIn(0f, 1f), y.toFloat().coerceIn(0f, 1f)))
                    }
                }
                if (points.size == count) add(points.sortedBy(BrushCurvePoint::x))
            }
        }
        return SutEffector(formatFlags, sourceFlags, pressureMinimum, tiltMinimum, velocityMinimum, randomMinimum, randomMinimumSigned, velocityScale, curves)
    }

    private fun ByteBuffer.percent(): Float = (int / 100f).coerceIn(0f, 1f)
}
