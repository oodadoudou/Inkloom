package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SystemBrushCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class BrushRoleOwnershipTest {
    @Test fun organizingOrdinaryImportedPaintNeverChangesItsTool() {
        val source = ImportedBrush(name = "Paint", format = BrushFormat.PROCREATE,
            compatibility = Compatibility.BASIC, sizeBytes = 1, report = emptyList())
        for (label in listOf("Erasers", "橡皮", "消しゴム", "Blend", "Smudge", "Liquify", "液化")) {
            assertEquals(label, EditorTool.PEN, BrushRoleClassifier.editorTool(source.copy(name = label)))
            assertEquals(label, EditorTool.PEN, BrushRoleClassifier.editorTool(source.copy(groupName = label)))
            assertEquals(label, EditorTool.PEN, BrushRoleClassifier.editorTool(source.copy(tags = listOf(label))))
        }
    }

    @Test fun liquifyModesSurviveRenamingAndMisleadingTags() {
        val source = SystemBrushCatalog.brushes.first { it.id == "system-liquify-twirl-left" }
        val renamed = source.copy(name = "Push", groupName = "Paint", tags = listOf("push-right"))
        assertEquals(app.inkloom.document.LiquifyMode.TWIRL_COUNTERCLOCKWISE, renamed.toolBinding.liquifyMode)
        assertEquals(EditorTool.LIQUIFY, BrushRoleClassifier.editorTool(renamed))
    }

    @Test fun factoryRolesSurviveAllOrganizationChanges() {
        val examples = mapOf("system-hard-eraser" to EditorTool.ERASER,
            "system-blend-smear" to EditorTool.SMUDGE, "system-liquify-push" to EditorTool.LIQUIFY,
            "system-hb-pencil" to EditorTool.PEN)
        for ((id, expected) in examples) {
            val brush = SystemBrushCatalog.brushes.first { it.id == id }
            val renamed = brush.copy(name = "Neutral", groupName = "Favorites", tags = emptyList())
            assertEquals(id, expected, BrushRoleClassifier.editorTool(renamed))
            assertEquals(id, expected, BrushRoleClassifier.editorTool(renamed.copy(groupName = "Erasers")))
        }
    }
}
