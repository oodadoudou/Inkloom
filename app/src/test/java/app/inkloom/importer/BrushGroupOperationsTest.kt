package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushGroupOperationsTest {
    @Test fun groupNamesAreNormalizedDeduplicatedAndOrdered() {
        val brushes = listOf(brush("A", "  Inking   pens "), brush("B", "watercolor"), brush("C", "INKING PENS"), brush("D", ""))

        assertEquals(listOf("Inking pens", "watercolor"), BrushGroupOperations.names(brushes))
    }

    @Test fun groupNamesKeepTheSavedLibraryOrderInsteadOfAlphabetizing() {
        val brushes = listOf(brush("Z", "Watercolor"), brush("A", "Inking"), brush("B", "watercolor"))

        assertEquals(listOf("Watercolor", "Inking"), BrushGroupOperations.names(brushes))
    }

    @Test fun assignRenameMergeAndUngroupPreserveUnrelatedBrushes() {
        val first = BrushGroupOperations.assign(brush("A", ""), "  Sketch   tools ")
        val second = brush("B", "Paint")
        val third = brush("C", "paint")

        assertEquals("Sketch tools", first.groupName)
        val merged = BrushGroupOperations.rename(listOf(first, second, third), "PAINT", " Sketch tools ")
        assertEquals(listOf("Sketch tools", "Sketch tools", "Sketch tools"), merged.map(ImportedBrush::groupName))
        val ungrouped = BrushGroupOperations.rename(merged, "Sketch tools", "   ")
        assertEquals(listOf("", "", ""), ungrouped.map(ImportedBrush::groupName))
    }

    @Test fun groupNamesAreBoundedForStorageAndUi() {
        assertEquals(BrushGroupOperations.MAX_NAME_LENGTH, BrushGroupOperations.normalize("x".repeat(200)).length)
    }

    @Test fun nestedPathsNormalizeSegmentsAndExposeAStableTree() {
        val brushes = listOf(brush("A", " Drawing / Ink / Pens "), brush("B", "Drawing/Pencil"), brush("C", "Painting/Oil"))

        assertEquals("Drawing/Ink/Pens", BrushGroupOperations.normalize(brushes[0].groupName))
        assertEquals(listOf("Drawing", "Drawing/Ink", "Drawing/Ink/Pens", "Drawing/Pencil", "Painting", "Painting/Oil"), BrushGroupOperations.treePaths(brushes))
        assertEquals(2, BrushGroupOperations.depth("Drawing/Ink/Pens"))
        assertEquals("Pens", BrushGroupOperations.displayName("Drawing/Ink/Pens"))
        assertEquals("Drawing/Ink", BrushGroupOperations.parent("Drawing/Ink/Pens"))
        assertTrue(BrushGroupOperations.isWithin("Drawing/Ink/Pens", "drawing"))
        assertFalse(BrushGroupOperations.isWithin("Painting/Oil", "Drawing"))
    }

    @Test fun groupTreeKeepsLateDiscoveredChildrenBesideTheirParent() {
        val brushes = listOf(brush("A", "Drawing"), brush("B", "Painting/Oil"), brush("C", "Drawing/Ink/Pens"))

        assertEquals(listOf("Drawing", "Drawing/Ink", "Drawing/Ink/Pens", "Painting", "Painting/Oil"), BrushGroupOperations.treePaths(brushes))
    }

    @Test fun renamingAParentMovesItsEntireSubtree() {
        val brushes = listOf(brush("A", "Drawing/Ink/Pens"), brush("B", "Drawing/Pencil"), brush("C", "Painting/Oil"))

        val renamed = BrushGroupOperations.rename(brushes, "Drawing", "Sketch")
        val promoted = BrushGroupOperations.rename(renamed, "Sketch/Ink", "")

        assertEquals(listOf("Sketch/Ink/Pens", "Sketch/Pencil", "Painting/Oil"), renamed.map(ImportedBrush::groupName))
        assertEquals(listOf("Pens", "Sketch/Pencil", "Painting/Oil"), promoted.map(ImportedBrush::groupName))
    }

    @Test fun moveReordersOnlyTheSelectedBrushGroup() {
        val brushes = listOf(brush("A", "Pens"), brush("B", "Pens"), brush("C", "Pencils"))
        val moved = BrushGroupOperations.move(brushes, "A", 1)

        assertEquals(listOf("B", "A"), moved.filter { it.groupName == "Pens" }.sortedBy { it.sortOrder }.map { it.id })
        assertEquals(0, moved.single { it.id == "C" }.sortOrder)
    }

    @Test fun moveGroupReordersWholeGroupsWithoutMovingUngroupedEntries() {
        val brushes = listOf(brush("P1", "Pens"), brush("W1", "Water"), brush("P2", "Pens"), brush("U", ""), brush("I1", "Ink"))

        val moved = BrushGroupOperations.moveGroup(brushes, "Ink", -2)

        assertEquals(listOf("Ink", "Pens", "Water"), BrushGroupOperations.names(moved))
        assertEquals(listOf("I1", "P1", "P2", "U", "W1"), moved.map(ImportedBrush::id))
    }

    @Test fun moveGroupReordersNestedSiblingSubtreesWithoutMovingOtherGroups() {
        val brushes = listOf(brush("P", "Drawing/Pencil"), brush("O", "Painting/Oil"), brush("I", "Drawing/Ink/Pens"), brush("U", ""))

        val moved = BrushGroupOperations.moveGroup(brushes, "Drawing/Ink", -1)

        assertEquals(listOf("I", "O", "P", "U"), moved.map(ImportedBrush::id))
        assertEquals(listOf("Drawing/Ink/Pens", "Painting/Oil", "Drawing/Pencil"), BrushGroupOperations.names(moved))
    }

    @Test fun batchAssignmentChangesOnlySelectedBrushes() {
        val brushes = listOf(brush("A", "Pens"), brush("B", "Pens"), brush("C", "Paint"))

        val moved = BrushGroupOperations.assignMany(brushes, setOf("A", "C"), "  Favorites  ")

        assertEquals(listOf("Favorites", "Pens", "Favorites"), moved.map(ImportedBrush::groupName))
    }

    @Test fun batchCopiesKeepSourceAssetsAndReceiveFreshLibraryIdentity() {
        var nextId = 0
        val brushes = listOf(brush("A", "Pens").copy(sourcePath = "/source/a.sut", favorite = true, hidden = true), brush("B", "Paint"))

        val copied = BrushGroupOperations.duplicateMany(brushes, setOf("A", "B"), "Copies", "副本") { "copy-${nextId++}" }

        assertEquals(listOf("A", "B", "copy-0", "copy-1"), copied.map(ImportedBrush::id))
        assertEquals(listOf("A 副本", "B 副本"), copied.takeLast(2).map(ImportedBrush::name))
        assertEquals(listOf("Copies", "Copies"), copied.takeLast(2).map(ImportedBrush::groupName))
        assertEquals("/source/a.sut", copied[2].sourcePath)
        assertFalse(copied[2].favorite)
        assertFalse(copied[2].hidden)
    }

    @Test fun batchFlagsAndRemovalPreserveEveryUnselectedPreset() {
        val brushes = listOf(brush("A", "Pens"), brush("B", "Pens"), brush("C", "Paint"))
        val selected = setOf("A", "C")

        val favorite = BrushGroupOperations.setFavorite(brushes, selected, true)
        val hidden = BrushGroupOperations.setHidden(favorite, selected, true)
        val remaining = BrushGroupOperations.removeMany(hidden, selected)

        assertTrue(hidden.single { it.id == "A" }.favorite)
        assertTrue(hidden.single { it.id == "C" }.hidden)
        assertEquals(listOf("B"), remaining.map(ImportedBrush::id))
        assertEquals(brushes[1], remaining.single())
    }

    @Test fun reimportDeduplicationKeepsIntentionalLibraryCopies() {
        val original = brush("A", "Pens").copy(sha256 = "same-hash", tipPreviewPath = "/tip.png")
        val copy = original.copy(id = "A-copy", name = "A copy")
        val reimport = original.copy(id = "new-import")

        val merged = BrushGroupOperations.mergeImports(listOf(original, copy), listOf(reimport, brush("B", "Paint")))

        assertEquals(listOf("A", "A-copy", "B"), merged.map(ImportedBrush::id))
    }

    private fun brush(name: String, group: String) = ImportedBrush(
        id = name,
        name = name,
        format = BrushFormat.IMAGE,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 1,
        report = emptyList(),
        groupName = group,
    )
}
