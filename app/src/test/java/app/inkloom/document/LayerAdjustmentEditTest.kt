package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class LayerAdjustmentEditTest {
    @Test fun newCorrectionKindsPersistWithGroupMasksAndUndoAsOneEdit() {
        for (kind in listOf(LayerAdjustmentKind.HUE_SATURATION, LayerAdjustmentKind.POSTERIZE,
            LayerAdjustmentKind.INVERT, LayerAdjustmentKind.THRESHOLD)) {
            val original = LayerAdjustment(kind)
            val changed = original.copy(luminosity = .4f, posterizeLevels = 3, threshold = .7f)
            val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
            val layer = InkLayer(id = "adjust", name = "Adjust", role = LayerRole.ADJUSTMENT,
                parentId = folder.id, mask = LayerMask(baseVisible = false), adjustment = original)
            val page = InkPage(layers = listOf(folder, layer))
            val edit = LayerAdjustmentEdit(layer.id, original, changed)
            val applied = requireNotNull(edit.apply(page, undo = false))
            val restored = Json.decodeFromString<InkPage>(Json.encodeToString(applied))
            assertEquals(applied, restored)
            assertEquals(page, edit.apply(restored, undo = true))
        }
        val legacy = Json.decodeFromString<LayerAdjustment>("{\"kind\":\"HUE_SATURATION\",\"saturation\":0.25}")
        assertEquals(0f, legacy.luminosity, 0f)
        assertEquals(.25f, legacy.saturation, 0f)
    }

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
