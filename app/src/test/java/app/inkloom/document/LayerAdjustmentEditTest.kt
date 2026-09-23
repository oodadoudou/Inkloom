package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class LayerAdjustmentEditTest {
    @Test fun parameterPreviewCanCommitAndUndoWithoutChangingOtherLayers() {
        val before = LayerAdjustment(kind = LayerAdjustmentKind.BRIGHTNESS_CONTRAST)
        val after = before.copy(brightness = .3f, contrast = .2f)
        val correction = InkLayer(id = "correction", name = "Correction", role = LayerRole.ADJUSTMENT,
            adjustment = before)
        val ink = InkLayer(id = "ink", name = "Ink")
        val page = InkPage(layers = listOf(ink, correction))
        val edit = LayerAdjustmentEdit(correction.id, before, after)
        val committed = requireNotNull(edit.apply(page, undo = false))
        assertEquals(after, committed.layers.last().adjustment)
        assertEquals(ink, committed.layers.first())
        assertEquals(page, edit.apply(committed, undo = true))
        assertNull(edit.apply(page, undo = true))
        assertNull(edit.apply(committed, undo = false))
    }

    @Test fun correctionParametersAndMaskSurviveNotebookSerialization() {
        val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER)
        val correction = InkLayer(id = "correction", name = "Curve", role = LayerRole.ADJUSTMENT,
            parentId = group.id, mask = LayerMask(baseVisible = false),
            adjustment = LayerAdjustment(kind = LayerAdjustmentKind.CURVES,
                curve = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(.5f, .8f), AdjustmentCurvePoint(1f, 1f))))
        val page = InkPage(layers = listOf(group, correction))
        val restored = Json.decodeFromString<InkPage>(Json.encodeToString(page))
        assertEquals(page, restored)
    }
}
