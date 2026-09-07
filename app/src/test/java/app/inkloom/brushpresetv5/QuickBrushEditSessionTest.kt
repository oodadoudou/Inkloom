package app.inkloom.brushpresetv5

import app.inkloom.model.BrushFormat
import app.inkloom.model.SystemBrushCatalog
import org.junit.Assert.*
import org.junit.Test

class QuickBrushEditSessionTest {
    private val factory get() = SystemBrushCatalog.brushes.first { it.id == "system-hb-pencil" }

    @Test fun factoryGestureChangesCanonicalFieldsAndAllocatesOnlyAtCommit() {
        val source = factory
        val original = BrushPresetV5Import.requirePreset(source)
        var draft = QuickBrushEditSession.start(source)
        repeat(30) {
            draft = draft.change("flow", (it + 1) / 100f)
            assertEquals(source.id, draft.current.identity.id)
        }
        val saved = draft.commit()
        val preset = BrushPresetV5Import.requirePreset(saved)
        assertEquals(BrushFormat.INKLOOM, saved.format)
        assertNotEquals(source.id, saved.id)
        assertEquals(saved.id, preset.identity.id)
        assertEquals(source.id, saved.derivedFromFactoryId)
        assertEquals(original.identity.version + 1, preset.identity.version)
        assertEquals(original.identity.renderBrushId, preset.identity.renderBrushId)
        assertEquals(original.renderSeed, preset.renderSeed)
        assertEquals(.3f, saved.flow, 0f)
        assertEquals(.3f, preset.deposition.flow, 0f)
        assertEquals(original, BrushPresetV5Import.requirePreset(factory))
    }

    @Test fun userEditKeepsIdentityAndUpdatesPressureAndRevision() {
        val user = QuickBrushEditSession.start(factory).change("flow", .25f).commit()
        val before = BrushPresetV5Import.requirePreset(user)
        val saved = QuickBrushEditSession.start(user).change("pressureSizeEnabled", 0f).commit()
        val after = BrushPresetV5Import.requirePreset(saved)
        assertEquals(user.id, saved.id)
        assertEquals(user.name, saved.name)
        assertEquals(before.identity.version + 1, after.identity.version)
        assertEquals(before.identity.renderBrushId, after.identity.renderBrushId)
        assertEquals(before.renderSeed, after.renderSeed)
        assertFalse(saved.sizePressureEnabled)
        assertFalse(after.dynamics.pressure.widthEnabled)
    }

    @Test fun unchangedAndDiscardedDraftsLeaveTheFactoryAlone() {
        val source = factory
        val session = QuickBrushEditSession.start(source)
        assertSame(source, session.commit())
        assertSame(source, session.change("flow", .1f).change("flow", source.flow).commit())
        session.change("flow", .2f)
        assertFalse(session.changed)
        assertSame(source, session.commit())
    }

    @Test fun unsupportedOptionsAndNonFiniteInputAreExplicitlyRejected() {
        val session = QuickBrushEditSession.start(factory)
        assertThrows(IllegalArgumentException::class.java) { session.change("missing-option", .5f) }
        assertThrows(IllegalArgumentException::class.java) { session.change("flow", Float.NaN) }
        val smudge = SystemBrushCatalog.brushes.first { it.id == "system-blend-smear" }
        assertTrue(assertThrows(IllegalArgumentException::class.java) { QuickBrushEditSession.start(smudge) }.message!!.contains("tool controls"))
    }

    @Test fun programIdentityMismatchCannotSilentlyProduceACopy() {
        assertThrows(IllegalArgumentException::class.java) { QuickBrushEditSession.start(factory.copy(id = "mismatched-import")) }
    }

    @Test fun eraserAuthorEditsKeepTheEraserOutputAndRole() {
        val source = SystemBrushCatalog.brushes.first { it.id == "system-hard-eraser" }
        val saved = QuickBrushEditSession.start(source).change("flow", .3f).commit()
        assertEquals(source.toolBinding, saved.toolBinding)
        assertEquals(BrushOutput.ERASE, BrushPresetV5Import.requirePreset(saved).output)
    }
}
