package app.inkloom.engine.v5.diagnostics

import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.model.SystemBrushCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrushExecutionFingerprintTest {
    @Test fun factoryIdentityCarriesSourceAndSemanticProgramIdentity() {
        val gPen = identity("system-g-pen")
        val pencil = identity("system-hb-pencil")
        val painting = identity("system-dry-painting-brush")

        assertEquals("FACTORY", gPen.origin)
        assertEquals("SYSTEM", gPen.sourceFormat)
        assertEquals(gPen.presetId, gPen.sourceBrushId)
        assertNotEquals(gPen.compiledProgramHash, pencil.compiledProgramHash)
        assertNotEquals(gPen.compiledProgramHash, painting.compiledProgramHash)
        assertNotEquals(pencil.compiledProgramHash, painting.compiledProgramHash)
        assertNotEquals(gPen.dynamicsHash, pencil.dynamicsHash)
        assertNotEquals(gPen.depositionHash, painting.depositionHash)
    }

    @Test(expected = IllegalStateException::class)
    fun mismatchedLibraryRecordAndCompiledProgramFailsClosed() {
        val source = requireNotNull(SystemBrushCatalog.byId("system-hb-pencil"))
        val other = requireNotNull(SystemBrushCatalog.byId("system-g-pen"))
        BrushExecutionFingerprints.identity(
            CompiledBrushProgramV5.compile(BrushPresetV5Import.requirePreset(other)),
            source,
        )
    }

    private fun identity(id: String): BrushExecutionIdentity {
        val source = requireNotNull(SystemBrushCatalog.byId(id))
        return BrushExecutionFingerprints.identity(
            CompiledBrushProgramV5.compile(BrushPresetV5Import.requirePreset(source)),
            source,
        )
    }
}
