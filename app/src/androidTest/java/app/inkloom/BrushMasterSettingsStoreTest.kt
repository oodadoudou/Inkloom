package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.settings.BrushMasterSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushMasterSettingsStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("brush_preset_sizes", 0)

    @Test fun mastersRemainIndependentForBrushesAndRolesAcrossRecreation() {
        preferences.edit().clear().commit()
        try {
            BrushMasterSettingsStore(context).apply {
                save(EditorTool.PEN, "same-preset", 11f, .32f)
                save(EditorTool.ERASER, "same-preset", 47f, .73f)
                save(EditorTool.PEN, "imported-sut-pencil", 6.5f, .18f)
            }
            val restored = BrushMasterSettingsStore(context)
            assertEquals(11f, restored.loadSize(EditorTool.PEN, "same-preset", 1f), .001f)
            assertEquals(.32f, restored.loadOpacity(EditorTool.PEN, "same-preset", 1f), .001f)
            assertEquals(47f, restored.loadSize(EditorTool.ERASER, "same-preset", 1f), .001f)
            assertEquals(.73f, restored.loadOpacity(EditorTool.ERASER, "same-preset", 1f), .001f)
            assertEquals(6.5f, restored.loadSize(EditorTool.PEN, "imported-sut-pencil", 1f), .001f)
            assertEquals(.18f, restored.loadOpacity(EditorTool.PEN, "imported-sut-pencil", 1f), .001f)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun legacySizesSurviveAndCorruptValuesUseFiniteBoundedDefaults() {
        preferences.edit().clear().putFloat("size:old-pencil", 13.5f)
            .putFloat("size:PEN:invalid", Float.NaN).putString("opacity:PEN:invalid", "broken").commit()
        try {
            val store = BrushMasterSettingsStore(context)
            assertEquals(13.5f, store.loadSize(EditorTool.PEN, "old-pencil", 4f), .001f)
            assertEquals(.68f, store.loadOpacity(EditorTool.PEN, "old-pencil", .68f), .001f)
            store.save(EditorTool.PEN, "old-pencil", 22f, .4f)
            assertEquals(22f, store.loadSize(EditorTool.PEN, "old-pencil", 4f), .001f)
            assertEquals(5f, store.loadSize(EditorTool.PEN, "invalid", 5f), .001f)
            assertEquals(.6f, store.loadOpacity(EditorTool.PEN, "invalid", .6f), .001f)
            assertEquals(.5f, store.loadSize(EditorTool.PEN, "missing", Float.NaN), .001f)
            assertEquals(1f, store.loadOpacity(EditorTool.PEN, "missing", Float.POSITIVE_INFINITY), .001f)
            assertEquals(400f, store.saveSize(EditorTool.PEN, "range", 1000f), .001f)
            assertEquals(.05f, store.saveOpacity(EditorTool.PEN, "range", -1f), .001f)
        } finally { preferences.edit().clear().commit() }
    }
}
