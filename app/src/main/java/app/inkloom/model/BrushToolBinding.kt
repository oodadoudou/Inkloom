package app.inkloom.model

import app.inkloom.document.LiquifyMode
import kotlinx.serialization.Serializable

/** Executable tool identity, independent of editable names, groups and tags. */
@Serializable
/** CLONE is the Blend tool's copy stamp: it paints pixels taken from a source point. */
enum class BrushToolRole { PAINT, ERASE, SMUDGE, LIQUIFY, UNSUPPORTED, CLONE }

@Serializable
data class BrushToolBinding(
    val role: BrushToolRole = BrushToolRole.PAINT,
    val liquifyMode: LiquifyMode = LiquifyMode.PUSH,
    val unsupportedReason: String? = null,
) {
    val isSupported: Boolean get() = role != BrushToolRole.UNSUPPORTED

    companion object {
        fun decode(role: String, mode: String, reason: String? = null): BrushToolBinding {
            val parsedRole = BrushToolRole.entries.firstOrNull { it.name == role }
                ?: return unavailable("Unknown stored brush tool role: $role")
            if (parsedRole == BrushToolRole.UNSUPPORTED) return unavailable(reason ?: "Stored brush tool is unsupported")
            val parsedMode = LiquifyMode.entries.firstOrNull { it.name == mode }
            if (parsedRole == BrushToolRole.LIQUIFY && parsedMode == null) {
                return unavailable("Unknown stored liquify mode: $mode")
            }
            return BrushToolBinding(parsedRole, parsedMode ?: LiquifyMode.PUSH)
        }

        fun unavailable(reason: String) = BrushToolBinding(BrushToolRole.UNSUPPORTED, unsupportedReason = reason)
    }
}
