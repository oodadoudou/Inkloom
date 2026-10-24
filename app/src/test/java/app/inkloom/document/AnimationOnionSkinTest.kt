package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationOnionSkinTest {
    private fun page(skin: OnionSkin?, current: Int = 2, folderVisible: Boolean = true, celOpacity: Float = 1f): Pair<List<InkLayer>, List<String>> {
        val folder = InkLayer(id = "f", name = "Walk", role = LayerRole.FOLDER, visible = folderVisible)
        val cels = (0 until 5).map { InkLayer(id = "c$it", name = "Cel $it", parentId = "f", visible = it == current, opacity = if (it == 1) celOpacity else 1f) }
        val animation = AnimationFolder(cels = cels.map { AnimationCel(it.id) }, current = "c$current", onionSkin = skin)
        return (listOf(folder.copy(animation = animation)) + cels) to cels.map { it.id }
    }

    @Test fun nearerCelsShowStrongerWithPreviousBlueAndNextGreen() {
        val (layers, _) = page(OnionSkin(before = 2, after = 1, opacity = .4f))
        val onion = AnimationOnionSkin.cels(layers)
        assertEquals(setOf("c0", "c1", "c3"), onion.keys)
        assertEquals(.4f, onion.getValue("c1").opacity, 1e-4f)
        assertEquals(.2f, onion.getValue("c0").opacity, 1e-4f)
        assertEquals(.4f, onion.getValue("c3").opacity, 1e-4f)
        assertEquals(AnimationOnionSkin.PREVIOUS, onion.getValue("c0").tint)
        assertEquals(AnimationOnionSkin.NEXT, onion.getValue("c3").tint)
    }

    @Test fun onlyAShownFolderWithOnionSkinOnShowsItsNeighboursWithinTheTimeline() {
        assertTrue(AnimationOnionSkin.cels(page(null).first).isEmpty())
        assertTrue(AnimationOnionSkin.cels(page(OnionSkin(), folderVisible = false).first).isEmpty())
        // At the first cel there is nothing before; counts are capped.
        assertEquals(setOf("c1", "c2", "c3", "c4"), AnimationOnionSkin.cels(page(OnionSkin(before = 3, after = 99), current = 0).first).keys)
        // A cel's own opacity carries into its onion skin.
        assertEquals(.35f * .5f, AnimationOnionSkin.cels(page(OnionSkin(), celOpacity = .5f).first).getValue("c1").opacity, 1e-4f)
        // Saved files without onion skin settings load with it off.
        val folder = kotlinx.serialization.json.Json.decodeFromString(AnimationFolder.serializer(), """{"frameRate":8,"cels":[{"layerId":"a"}],"current":"a"}""")
        assertEquals(null, folder.onionSkin)
    }
}
