package app.inkloom.document

import kotlin.math.cos
import kotlin.math.sin

object ImageElementHitTest {
    fun find(page: InkPage, x: Float, y: Float): PageElement.Image? {
        if (!x.isFinite() || !y.isFinite()) return null
        val order = page.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
        return page.elements.filterIsInstance<PageElement.Image>()
            .sortedBy { order[it.layerId] ?: -1 }.asReversed().firstOrNull { image ->
                val layer = page.layers.firstOrNull { it.id == image.layerId }
                if (layer == null || !LayerHierarchy.effectiveVisible(page.layers, layer) ||
                    LayerHierarchy.effectiveLocked(page.layers, layer) || image.opacity <= 0f) return@firstOrNull false
                val dx = x * page.width - (image.x * page.width + image.width * .5f)
                val dy = y * page.height - (image.y * page.height + image.height * .5f)
                val angle = Math.toRadians(image.rotationDegrees.toDouble())
                val localX = dx * cos(angle) + dy * sin(angle)
                val localY = -dx * sin(angle) + dy * cos(angle)
                kotlin.math.abs(localX) <= image.width * .5f && kotlin.math.abs(localY) <= image.height * .5f
            }
    }
}
