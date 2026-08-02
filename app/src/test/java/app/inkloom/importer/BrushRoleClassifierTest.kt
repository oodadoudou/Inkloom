package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushRoleClassifierTest {
    private fun brush(name: String, group: String = "", explicit: Boolean = false) = ImportedBrush(
        name = name,
        format = BrushFormat.SUT,
        compatibility = Compatibility.HIGH,
        sizeBytes = 1,
        report = emptyList(),
        groupName = group,
        isEraser = explicit,
    )

    @Test fun `recognizes explicit and common localized eraser roles`() {
        assertTrue(BrushRoleClassifier.isEraser(brush("Texture", explicit = true)))
        assertTrue(BrushRoleClassifier.isEraser(brush("Soft", "橡皮")))
        assertTrue(BrushRoleClassifier.isEraser(brush("練り消しゴム")))
        assertTrue(BrushRoleClassifier.isEraser(brush("Hard Eraser")))
        assertFalse(BrushRoleClassifier.isEraser(brush("G Pen", "钢笔")))
    }
}
