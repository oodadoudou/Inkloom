package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSetLibraryTest {
    private val red = 0xffff2222.toInt()
    private val blue = 0xff2255ff.toInt()

    @Test fun sameColorTilesRetainDifferentIdsAndNamesThroughEditing() {
        val initial = ColorSetLibrary().createSet("画面", "set")
            .insertSwatch("set", red, "肤色暖部", id = "first")
            .insertSwatch("set", red, "衣服暖部", id = "second")
        val edited = initial.renameSwatch("set", "first", "背景暖部")
            .replaceSwatch("set", "first", blue)
        assertEquals(listOf("second", "first"), edited.activeSet!!.swatches.map { it.id })
        assertEquals(listOf(red, blue), edited.activeSet!!.swatches.map { it.color })
        assertEquals(listOf("衣服暖部", "背景暖部"), edited.activeSet!!.swatches.map { it.name })
        assertEquals("second", edited.activeSet!!.selectedSwatchId)
        assertEquals(listOf(red, red), initial.activeSet!!.swatches.map { it.color })
    }

    @Test fun addInsertsBeforeSelectedTileAndSelectsTheNewIdentity() {
        val initial = ColorSetLibrary().createSet("Set", "set")
            .insertSwatch("set", red, id = "a")
            .insertSwatch("set", blue, beforeIndex = 1, id = "b")
            .selectSwatch("set", "a")
        val added = initial.insertSwatch("set", red, id = "c")
        assertEquals(listOf("c", "a", "b"), added.activeSet!!.swatches.map { it.id })
        assertEquals("c", added.activeSet!!.selectedSwatchId)
    }

    @Test fun movingAcrossMultiplePositionsUsesFinalIndexAndKeepsSelection() {
        val initial = ColorSetLibrary().createSet("Set", "set")
            .insertSwatch("set", red, beforeIndex = 0, id = "a")
            .insertSwatch("set", red, beforeIndex = 1, id = "b")
            .insertSwatch("set", blue, beforeIndex = 2, id = "c")
            .selectSwatch("set", "b")
        val moved = initial.moveSwatch("set", "a", 2)
        assertEquals(listOf("b", "c", "a"), moved.activeSet!!.swatches.map { it.id })
        assertEquals("b", moved.activeSet!!.selectedSwatchId)
        assertEquals(initial, moved.moveSwatch("set", "a", 0))
    }

    @Test fun duplicateCreatesIndependentIdentitiesAndSelectsTheNewSet() {
        val initial = ColorSetLibrary().createSet("Original", "set")
            .insertSwatch("set", red, "Warm", id = "a")
            .insertSwatch("set", red, "Cool", beforeIndex = 1, id = "b")
        val duplicated = initial.duplicateSet("set", "Copy", "copy")
        val copy = duplicated.activeSet!!
        assertEquals("copy", duplicated.activeSetId)
        assertEquals(listOf("set", "copy"), duplicated.sets.map { it.id })
        assertEquals(listOf("Warm", "Cool"), copy.swatches.map { it.name })
        assertEquals(listOf(red, red), copy.swatches.map { it.color })
        assertTrue(copy.swatches.none { it.id in initial.activeSet!!.swatches.map { old -> old.id } })
        assertEquals(copy.swatches.last().id, copy.selectedSwatchId)
        val edited = duplicated.replaceSwatch("copy", copy.swatches.first().id, blue)
        assertEquals(red, edited.sets.first().swatches.first().color)
        assertEquals(blue, edited.sets.last().swatches.first().color)
    }

    @Test fun setSelectionAndReorderDoNotMoveSwatchesOrRenameOtherSets() {
        val initial = ColorSetLibrary().createSet("One", "one").insertSwatch("one", red, id = "red")
            .createSet("Two", "two").insertSwatch("two", blue, id = "blue")
            .createSet("Three", "three")
        val edited = initial.selectSet("one").renameSet("two", " Two renamed ").moveSet("one", 2)
        assertEquals(listOf("two", "three", "one"), edited.sets.map { it.id })
        assertEquals("one", edited.activeSetId)
        assertEquals("red", edited.activeSet!!.selectedSwatchId)
        assertEquals("Two renamed", edited.sets.first().name)
        assertEquals(blue, edited.sets.first().swatches.single().color)
    }

    @Test fun deletionChoosesAdjacentItemsAndLastDeletionLeavesAUsableEmptyLibrary() {
        val initial = ColorSetLibrary().createSet("One", "one").insertSwatch("one", red, id = "a")
            .insertSwatch("one", blue, beforeIndex = 1, id = "b").selectSwatch("one", "a")
            .createSet("Two", "two").selectSet("one")
        val deletedTile = initial.removeSwatch("one", "a")
        assertEquals("b", deletedTile.activeSet!!.selectedSwatchId)
        val noTiles = deletedTile.removeSwatch("one", "b")
        assertNull(noTiles.activeSet!!.selectedSwatchId)
        assertTrue(noTiles.activeSet!!.swatches.isEmpty())
        assertEquals("two", noTiles.deleteSet("one").activeSetId)
        val empty = noTiles.deleteSet("one").deleteSet("two")
        assertEquals(ColorSetLibrary(), empty)
        assertEquals(empty, ColorSetLibraryCodec.decode(ColorSetLibraryCodec.encode(empty)))
        assertEquals("New", empty.createSet("New").activeSet!!.name)
    }

    @Test fun removingUnselectedItemsKeepsActiveIdentities() {
        val initial = ColorSetLibrary().createSet("One", "one").insertSwatch("one", red, id = "a")
            .insertSwatch("one", blue, id = "b").createSet("Two", "two").selectSwatch("one", "b")
        val edited = initial.removeSwatch("one", "a").deleteSet("two")
        assertEquals("one", edited.activeSetId)
        assertEquals("b", edited.activeSet!!.selectedSwatchId)
    }

    @Test fun legacyMigrationPreservesOrderColorsAndMakesStableIds() {
        val colors = listOf(red, blue, red)
        val migrated = ColorSetLibrary.fromLegacy(colors, "已存颜色")
        assertEquals(colors, migrated.activeSet!!.swatches.map { it.color })
        assertEquals(3, migrated.activeSet!!.swatches.map { it.id }.distinct().size)
        assertEquals(migrated, ColorSetLibrary.fromLegacy(colors, "已存颜色"))
        assertTrue(ColorSetLibrary.fromLegacy(emptyList(), "已存颜色").activeSet!!.swatches.isEmpty())
    }

    @Test fun codecKeepsUnicodeAlphaDuplicateColorsSelectionsAndOrder() {
        val library = ColorSetLibrary().createSet("颜色 🌊", "one")
            .insertSwatch("one", red, "同色甲", id = "a")
            .insertSwatch("one", red, "同色乙", id = "b")
            .createSet("透明", "two").insertSwatch("two", 0x44221100, id = "alpha")
            .selectSwatch("one", "a")
        assertEquals(library, ColorSetLibraryCodec.decode(ColorSetLibraryCodec.encode(library)))
    }

    @Test fun staleIdsAndInvalidMovesAreExplicitAndLeaveSourceUntouched() {
        val library = ColorSetLibrary().createSet("Set", "set").insertSwatch("set", red, id = "a")
        rejects { library.selectSet("missing") }
        rejects { library.removeSwatch("set", "missing") }
        rejects { library.moveSwatch("set", "a", 1) }
        rejects { library.moveSet("set", -1) }
        rejects { library.insertSwatch("set", red, beforeIndex = 10) }
        rejects { library.createSet("Duplicate ID", "set") }
        rejects { library.insertSwatch("set", blue, id = "a") }
        assertEquals("a", library.activeSet!!.swatches.single().id)
        assertEquals(red, library.activeSet!!.swatches.single().color)
    }

    @Test fun namesAreValidatedWithoutSilentlyTruncatingData() {
        val library = ColorSetLibrary().createSet("Set", "set").insertSwatch("set", red, id = "a")
        rejects { library.renameSet("set", "   ") }
        rejects { library.renameSwatch("set", "a", "x".repeat(ColorSetLibrary.MAX_NAME_LENGTH + 1)) }
        rejects { library.renameSet("set", "bad\u0000name") }
        assertEquals("", library.renameSwatch("set", "a", "").activeSet!!.swatches.single().name)
    }

    @Test fun capacityIsExplicitAndNeverEvictsPreviouslySavedColors() {
        val set = NamedColorSet("set", "Full", List(ColorSetLibrary.MAX_SWATCHES_PER_SET) { ColorSetSwatch("s$it", it) })
        val library = ColorSetLibrary(listOf(set), "set").validated()
        rejects { library.insertSwatch("set", blue) }
        assertEquals(0, library.activeSet!!.swatches.first().color)
        assertEquals(ColorSetLibrary.MAX_SWATCHES_PER_SET, library.activeSet!!.swatches.size)
        val manySets = ColorSetLibrary(List(ColorSetLibrary.MAX_SETS) { NamedColorSet("set$it", "Set $it") }, "set0")
        rejects { manySets.createSet("One too many") }
        rejects { manySets.duplicateSet("set0", "One too many") }
    }

    @Test fun totalCapacityAlsoGuardsDuplication() {
        val library = ColorSetLibrary(List(4) { set ->
            NamedColorSet("set$set", "Set $set", List(4096) { ColorSetSwatch("s$set-$it", it) })
        }, "set0").validated()
        rejects { library.duplicateSet("set0", "Copy") }
        assertEquals(4, library.sets.size)
    }

    @Test fun corruptAndFutureLibrariesAreRejectedRatherThanResetToLegacyData() {
        rejects { ColorSetLibraryCodec.decode("not-json") }
        rejects { ColorSetLibraryCodec.decode("{\"version\":2}") }
        rejects { ColorSetLibraryCodec.decode("{\"sets\":[],\"activeSetId\":\"missing\"}") }
        val library = ColorSetLibrary().createSet("Set", "set").insertSwatch("set", red, id = "a")
        rejects { library.copy(sets = library.sets + library.sets.first()).validated() }
        rejects { library.copy(sets = listOf(library.sets.single().copy(selectedSwatchId = "missing"))).validated() }
        val duplicate = library.duplicateSet("set", "Copy", "copy")
        assertNotEquals(library.activeSetId, duplicate.activeSetId)
        rejects { duplicate.copy(sets = duplicate.sets.map { it.copy(swatches = listOf(ColorSetSwatch("same", red)), selectedSwatchId = null) }).validated() }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected an explicit invalid-operation rejection")
        } catch (_: IllegalArgumentException) {
            // Both model validation and serialization decode use IllegalArgumentException.
        }
    }
}
