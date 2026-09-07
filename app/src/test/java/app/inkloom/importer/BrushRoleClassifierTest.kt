package app.inkloom.importer

import app.inkloom.model.BrushToolBinding
import app.inkloom.model.BrushToolRole
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
        toolBinding = BrushToolBinding(if (explicit) BrushToolRole.ERASE else BrushToolRole.PAINT),
    )

    @Test fun `recognizes explicit erasers without interpreting localized labels`() {
        assertTrue(BrushRoleClassifier.isEraser(brush("Texture", explicit = true)))
        assertFalse(BrushRoleClassifier.isEraser(brush("Soft", "橡皮")))
        assertFalse(BrushRoleClassifier.isEraser(brush("練り消しゴム")))
        assertFalse(BrushRoleClassifier.isEraser(brush("Hard Eraser")))
        assertFalse(BrushRoleClassifier.isEraser(brush("G Pen", "钢笔")))
    }
}
