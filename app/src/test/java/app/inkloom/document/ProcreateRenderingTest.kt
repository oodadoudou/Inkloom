package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcreateRenderingTest {
    @Test fun archiveFlagsMapToAllSixPublicRenderingModes() {
        assertEquals(ProcreateRenderingMode.LIGHT_GLAZE, ProcreateRendering.fromArchive(true, false, false))
        assertEquals(ProcreateRenderingMode.UNIFORM_GLAZE, ProcreateRendering.fromArchive(false, true, false))
        assertEquals(ProcreateRenderingMode.INTENSE_GLAZE, ProcreateRendering.fromArchive(false, false, false))
        assertEquals(ProcreateRenderingMode.HEAVY_GLAZE, ProcreateRendering.fromArchive(true, false, true))
        assertEquals(ProcreateRenderingMode.UNIFORM_BLENDING, ProcreateRendering.fromArchive(false, true, true))
        assertEquals(ProcreateRenderingMode.INTENSE_BLENDING, ProcreateRendering.fromArchive(false, false, true))
    }

    @Test fun intenseModesDepositMoreWhileMaximumTransferModesAreIdentified() {
        val light = ProcreateRendering.depositedAlpha(ProcreateRenderingMode.LIGHT_GLAZE, .3f)
        val uniform = ProcreateRendering.depositedAlpha(ProcreateRenderingMode.UNIFORM_GLAZE, .3f)
        val intense = ProcreateRendering.depositedAlpha(ProcreateRenderingMode.INTENSE_GLAZE, .3f)

        assertTrue(light < uniform)
        assertTrue(intense > uniform)
        assertTrue(ProcreateRendering.usesMaximumTransfer(ProcreateRenderingMode.LIGHT_GLAZE))
        assertTrue(ProcreateRendering.usesMaximumTransfer(ProcreateRenderingMode.HEAVY_GLAZE))
        assertEquals(0f, ProcreateRendering.wetMixStrength(ProcreateRenderingMode.INTENSE_GLAZE), 0f)
        assertTrue(ProcreateRendering.wetMixStrength(ProcreateRenderingMode.INTENSE_BLENDING) > ProcreateRendering.wetMixStrength(ProcreateRenderingMode.UNIFORM_BLENDING))
    }
}
