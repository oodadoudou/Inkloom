package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class AnimationTest {
    private val names = AnimationNames()
    private val page = InkPage(layers = listOf(InkLayer(id = LayerIds.INK, name = "Ink")))

    private fun folder(p: InkPage) = p.layers.first { it.animation != null }

    @Test fun anAnimationFolderStartsWithOneShownCel() {
        val (created, cel) = AnimationEdits.createFolder(page, LayerIds.INK, names)
        val folder = folder(created)
        assertEquals(listOf(AnimationCel(cel)), folder.animation!!.cels)
        assertEquals(cel, folder.animation!!.current)
        assertEquals(folder.id, created.layers.first { it.id == cel }.parentId)
        assertTrue("the folder sits above the target", created.layers.indexOf(folder) > created.layers.indexOfFirst { it.id == LayerIds.INK })
    }

    @Test fun onlyTheCurrentCelIsShown() {
        val (created, first) = AnimationEdits.createFolder(page, LayerIds.INK, names)
        val (withSecond, second) = requireNotNull(AnimationEdits.addCel(created, folder(created).id, names))
        assertEquals(listOf(first, second), folder(withSecond).animation!!.cels.map { it.layerId })
        assertFalse(withSecond.layers.first { it.id == first }.visible)
        assertTrue(withSecond.layers.first { it.id == second }.visible)
        val back = AnimationEdits.show(withSecond, folder(withSecond).id, first)
        assertTrue(back.layers.first { it.id == first }.visible); assertFalse(back.layers.first { it.id == second }.visible)
    }

    @Test fun theTimelinePlaysCelsForTheirFrames() {
        val (created, first) = AnimationEdits.createFolder(page, LayerIds.INK, names)
        val (withSecond, second) = requireNotNull(AnimationEdits.addCel(created, folder(created).id, names))
        val timed = AnimationEdits.setFrames(withSecond, folder(withSecond).id, first, 3)
        val animation = folder(timed).animation!!
        assertEquals(4, animation.totalFrames)
        assertEquals(listOf(first, first, first, second, first), (0..4).map { AnimationEdits.celAt(animation, it)!!.layerId })
        val moved = AnimationEdits.move(timed, folder(timed).id, second, -1)
        assertEquals(listOf(second, first), folder(moved).animation!!.cels.map { it.layerId })
        val removed = AnimationEdits.removeFromTimeline(moved, folder(moved).id, second)
        assertEquals(listOf(first), folder(removed).animation!!.cels.map { it.layerId })
        assertTrue("a cel taken off the timeline is shown again", removed.layers.first { it.id == second }.visible)
    }

    @Test fun animationMaterialsCarryTheFolderAndPlaceWithNewIds() {
        val (created, cel) = AnimationEdits.createFolder(page, LayerIds.INK, names)
        val withText = created.copy(elements = listOf(PageElement.Text(x = 0f, y = 0f, text = "Hi", layerId = cel)))
        val template = AnimationEdits.subtree(withText, folder(withText).id)
        assertEquals(2, template.layers.size)
        assertEquals(1, template.elements.size)
        val material = Material(name = "Walk", kind = MaterialKind.ANIMATION, page = template)
        val placed = AnimationEdits.place(page, requireNotNull(material.page))
        val placedFolder = folder(placed)
        assertNotEquals(folder(created).id, placedFolder.id)
        val placedCel = placedFolder.animation!!.cels.single().layerId
        assertEquals(placedFolder.id, placed.layers.first { it.id == placedCel }.parentId)
        assertEquals(placedCel, placed.elements.single().layerId)
    }
}
