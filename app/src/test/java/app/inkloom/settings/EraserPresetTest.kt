package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserPresetTest {
    @Test fun `six distinct default eraser brushes are available`() {
        assertEquals(6, EraserPreset.entries.size)
        assertTrue(EraserPreset.NORMAL_FREE.usesMask)
        assertTrue(EraserPreset.HARD_EDGE.splitsStroke)
        assertFalse(EraserPreset.STROKE.usesMask)
        assertFalse(EraserPreset.STROKE.splitsStroke)
    }

    @Test fun `soft erasers encode partial or feathered removal`() {
        assertTrue(EraserPreset.SOFT.hardness < EraserPreset.SOFT_EDGE.hardness)
        assertTrue(EraserPreset.SOFT.opacity < 1f)
        assertEquals(1f, EraserPreset.SOFT_EDGE.opacity, 0f)
    }
}
