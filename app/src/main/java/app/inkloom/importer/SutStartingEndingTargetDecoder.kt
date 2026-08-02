package app.inkloom.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SutStartingEndingTarget(val parameterId: Int, val enabled: Boolean, val minimumPercent: Float)

object SutStartingEndingTargetDecoder {
    const val BRUSH_SIZE_PARAMETER_ID = 1001

    fun decode(blob: ByteArray?): List<SutStartingEndingTarget> {
        if (blob == null || blob.size < 12) return emptyList()
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)
        val recordBytes = buffer.int
        val count = buffer.int
        buffer.int // target-list format/version marker
        if (recordBytes != 12 || count !in 0..256 || buffer.remaining() < count * recordBytes) return emptyList()
        return buildList(count) {
            repeat(count) {
                val parameterId = buffer.int
                val enabled = buffer.int != 0
                val minimum = (buffer.int / 100f).coerceIn(0f, 1f)
                add(SutStartingEndingTarget(parameterId, enabled, minimum))
            }
        }
    }
}
