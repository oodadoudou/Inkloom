package app.inkloom.ui

import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushOptionDescriptors
import app.inkloom.brushpresetv5.BrushMaterialOptions
import app.inkloom.settings.UiLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEditorLabelsTest {
    @Test fun everyExposedDescriptorHasChineseTextIndependentOfTheDeviceLocale() {
        (BrushFamily.entries.flatMap(BrushOptionDescriptors::forFamily) + BrushMaterialOptions.all + app.inkloom.brushpresetv5.DualBrushEditorOptions.all).distinctBy { it.id }.forEach {
            assertTrue("Missing Chinese control ${it.id}", it.localizedLabel(UiLanguage.SIMPLIFIED_CHINESE).any { c -> c in '\u4e00'..'\u9fff' })
        }
    }
}
