package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.BrushPresetSizeStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushPresetSizeStoreTest {
    @Test
    fun sizesRemainIndependentForBrushesAndErasers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("brush_preset_sizes", 0).edit().clear().commit()
        val store = BrushPresetSizeStore(context)

        store.save("system-studio-pen", 11f)
        store.save("system-hard-eraser", 47f)
        store.save("imported-sut-pencil", 6.5f)

        assertEquals(11f, store.load("system-studio-pen", 1f), .001f)
        assertEquals(47f, store.load("system-hard-eraser", 1f), .001f)
        assertEquals(6.5f, store.load("imported-sut-pencil", 1f), .001f)
    }
}
