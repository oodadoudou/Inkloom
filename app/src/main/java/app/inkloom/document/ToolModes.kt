package app.inkloom.document

import kotlinx.serialization.Serializable

/** Native V5 correction sub-tool identifiers. Geometry is implemented only by V5 tools. */
enum class CorrectLineMode { SMOOTH, SIMPLIFY, ADJUST_WIDTH, REDRAW_WIDTH, CONNECT, CONTROL_POINTS }

/** Native V5 raster deformation modes. */
@Serializable
enum class LiquifyMode {
    PUSH,
    PUSH_LEFT,
    PUSH_RIGHT,
    TWIRL_CLOCKWISE,
    TWIRL_COUNTERCLOCKWISE,
    PINCH,
    EXPAND,
    CRYSTALS,
    EDGE,
    RECONSTRUCT,
}
