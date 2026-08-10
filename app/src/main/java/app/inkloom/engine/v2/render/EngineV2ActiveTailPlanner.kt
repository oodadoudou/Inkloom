package app.inkloom.engine.v2.render

import app.inkloom.document.InkStroke

/** Stateful front-buffer planner. Its state is owned by the render callback thread. */
internal class EngineV2ActiveTailPlanner {
    private val renderedPointCounts = mutableMapOf<String, Int>()

    data class Plan(
        val clearBeforeDraw: Boolean,
        val strokes: List<InkStroke>,
        val incrementalStampDraw: Boolean = false,
    )

    fun complete(strokes: List<InkStroke>): Plan {
        renderedPointCounts.clear()
        strokes.forEach { renderedPointCounts[it.id] = it.points.size }
        return Plan(clearBeforeDraw = true, strokes = strokes)
    }

    fun incremental(strokes: List<InkStroke>): Plan {
        if (strokes.any(::requiresCompleteReplay)) return complete(strokes)
        val activeIds = strokes.mapTo(mutableSetOf(), InkStroke::id)
        renderedPointCounts.keys.retainAll(activeIds)
        if (strokes.any(::isStamped)) {
            val changed = strokes.any { stroke -> stroke.points.size > (renderedPointCounts[stroke.id] ?: 0) }
            strokes.forEach { renderedPointCounts[it.id] = it.points.size }
            return Plan(
                clearBeforeDraw = false,
                strokes = if (changed) strokes else emptyList(),
                incrementalStampDraw = true,
            )
        }
        val tails = strokes.mapNotNull { stroke ->
            val previousCount = renderedPointCounts[stroke.id] ?: 0
            val currentCount = stroke.points.size
            if (currentCount <= previousCount) return@mapNotNull null
            renderedPointCounts[stroke.id] = currentCount
            val start = (previousCount - 1).coerceAtLeast(0)
            stroke.copy(points = stroke.points.subList(start, currentCount))
        }
        return Plan(clearBeforeDraw = false, strokes = tails)
    }

    fun reset() = renderedPointCounts.clear()

    private fun isStamped(stroke: InkStroke): Boolean =
        stroke.tipAssetPath != null || stroke.tipAssetPaths.isNotEmpty() || ProceduralBrushTip.supports(stroke)

    private fun requiresCompleteReplay(stroke: InkStroke): Boolean =
        // Active previews suppress the length-dependent ending taper; masks are the only effect
        // that can remove pixels which have already been presented in the front buffer.
        stroke.masks.isNotEmpty()
}
