package app.inkloom.ui

import app.inkloom.document.*
import org.junit.Assert.*
import org.junit.Test

class RetainedLayerRenderKeyTest {
    @Test fun appearanceAndRasterPublicationDoNotInvalidateRawRetainedContentButVisibilityAndAlphaOwnershipDo() {
        val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER)
        val child = InkLayer(id = "child", name = "Child", parentId = group.id)
        val element = PageElement.Shape(id = "shape", x = 0f, y = 0f, width = 30f, height = 30f, layerId = child.id)
        val page = InkPage(layers = listOf(group, child), elements = listOf(element))
        val key = RetainedLayerRenderKey.forPage(page)
        assertEquals(key, RetainedLayerRenderKey.forPage(page.copy(layers = listOf(
            group.copy(opacity = .2f, passThrough = true, mask = LayerMask(), collapsed = true),
            child.copy(blendMode = LayerBlendMode.MULTIPLY, locked = true, rasterRotationQuarterTurns = 2)))))
        assertNotEquals(key, RetainedLayerRenderKey.forPage(page.copy(layers = listOf(group.copy(visible = false), child))))
        assertNotEquals(key, RetainedLayerRenderKey.forPage(page.copy(layers = listOf(group,
            child.copy(alphaLock = true, alphaLockBaseContentIds = listOf(element.id))))))
        assertEquals(key, RetainedLayerRenderKey.forPage(page.copy(layers = page.layers.reversed())))
    }
}
