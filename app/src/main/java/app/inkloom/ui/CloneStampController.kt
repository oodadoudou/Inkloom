package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Paint
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.tools.RasterTileCloneSession
import kotlin.math.roundToInt

/** What the copy stamp's tool property buttons ask the canvas to do. */
enum class CloneCommand { PICK_SOURCE, CLEAR_SOURCE }

/**
 * The copy stamp (clone) brush on the canvas: its source point, the offset between source and
 * brush, and the stroke in progress. With [aligned] the offset set by the first stroke after
 * choosing a source is kept for later strokes; otherwise every stroke starts again at the source.
 */
internal class CloneStampController {
    var enabled = false
    var aligned = true
    var opacity = 1f
    var hardness = .6f
    /** The next touch chooses the source instead of painting. */
    var pickingSource = false
    /** Source point in page fractions, or null before one is chosen. */
    var source: Pair<Float, Float>? = null
        private set
    private var offset: Pair<Int, Int>? = null
    private var strokeOffset: Pair<Int, Int>? = null
    private var session: RasterTileCloneSession? = null
    private var surface: PixelTileSurface? = null
    private var lastX = 0f
    private var lastY = 0f

    val stroking: Boolean get() = session != null
    val previewSnapshot: PixelTileSurfaceSnapshot? get() = session?.previewSnapshot()
    val needsSource: Boolean get() = pickingSource || source == null

    fun command(command: CloneCommand) = when (command) {
        CloneCommand.PICK_SOURCE -> pickingSource = true
        CloneCommand.CLEAR_SOURCE -> { source = null; offset = null; pickingSource = false }
    }

    fun setSource(x: Float, y: Float) { source = x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f); offset = null; pickingSource = false }

    /** Starts a stroke at page fractions [x], [y]; false without a source. */
    fun begin(surface: PixelTileSurface, selection: MaskTileSurfaceSnapshot?, alphaLocked: Boolean, x: Float, y: Float): Boolean {
        val (sourceX, sourceY) = source ?: return false
        session?.close()
        val px = x * surface.logicalWidth; val py = y * surface.logicalHeight
        val strokeOffset = offset.takeIf { aligned }
            ?: ((sourceX * surface.logicalWidth - px).roundToInt() to (sourceY * surface.logicalHeight - py).roundToInt())
        if (aligned) offset = strokeOffset
        this.strokeOffset = strokeOffset
        session = RasterTileCloneSession(surface, selection, alphaLocked, strokeOffset.first, strokeOffset.second)
        this.surface = surface
        lastX = px; lastY = py
        return true
    }

    fun extend(x: Float, y: Float, radius: Float): PixelRect? {
        val active = session ?: return null
        val target = surface ?: return null
        val px = x * target.logicalWidth; val py = y * target.logicalHeight
        val changed = active.stroke(lastX, lastY, px, py, radius, opacity, hardness)
        lastX = px; lastY = py
        return changed
    }

    /** Ends the stroke; the record and its bounds when pixels changed, otherwise nothing. */
    fun finish(): Pair<PixelTileTransactionRecord, PixelRect>? {
        val active = session ?: return null
        session = null
        val bounds = active.dirtyBounds
        if (bounds == null) { active.close(); return null }
        return active.commit() to bounds
    }

    /** Abandons the stroke and returns the area to repaint. */
    fun cancel(): PixelRect? {
        val active = session ?: return null
        session = null
        val bounds = active.dirtyBounds
        active.close()
        return bounds
    }

    /** A crosshair where pixels are taken from: the source, or during a stroke the point under the brush plus the offset. */
    fun drawOverlay(canvas: Canvas, viewWidth: Float, viewHeight: Float, viewScale: Float, paint: Paint) {
        if (!enabled) return
        val target = surface
        val (x, y) = when {
            session != null && target != null -> strokeOffset?.let { (dx, dy) -> (lastX + dx) / target.logicalWidth to (lastY + dy) / target.logicalHeight }
                ?: return
            else -> source ?: return
        }
        val cx = x * viewWidth; val cy = y * viewHeight
        val arm = 12f / viewScale.coerceAtLeast(.05f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f / viewScale.coerceAtLeast(.05f); paint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(cx, cy, arm * .7f, paint); canvas.drawLine(cx - arm, cy, cx + arm, cy, paint); canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        paint.strokeWidth = 1.5f / viewScale.coerceAtLeast(.05f); paint.color = 0xFF202020.toInt()
        canvas.drawCircle(cx, cy, arm * .7f, paint); canvas.drawLine(cx - arm, cy, cx + arm, cy, paint); canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
    }
}
