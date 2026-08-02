package app.inkloom.importer

enum class SutRotationMode { FIXED, DIRECTION_OF_PEN, PEN_TWIST, DIRECTION_OF_LINE, UNKNOWN }

data class SutRotationSettings(val mode: SutRotationMode, val rawMode: Int, val additionalDynamicsFlags: Int)

object SutRotationDecoder {
    fun decode(flags: Int): SutRotationSettings {
        val rawMode = flags and MODE_MASK
        val mode = when (rawMode) {
            0 -> SutRotationMode.FIXED
            1 -> SutRotationMode.DIRECTION_OF_PEN
            2 -> SutRotationMode.PEN_TWIST
            3 -> SutRotationMode.DIRECTION_OF_LINE
            else -> SutRotationMode.UNKNOWN
        }
        return SutRotationSettings(mode, rawMode, flags and SOURCE_BITS)
    }

    private const val MODE_MASK = 0x0F
    private const val SOURCE_BITS = 0xF0
}
