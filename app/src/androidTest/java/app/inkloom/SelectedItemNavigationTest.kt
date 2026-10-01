package app.inkloom

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.inkloom.model.EditorTool
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.*
import app.inkloom.ui.ColorSetPanel
import app.inkloom.ui.WorkspaceSubtoolsPanel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SelectedItemNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun subtoolsRevealActiveBrushButDoNotOverrideManualScrolling() {
        val brushes = (0..79).map { SystemBrushCatalog.brushes.first().copy(id = "test-$it", name = "Test pen $it") }
        val active = mutableStateOf("test-72")
        val open = mutableStateOf(true)
        compose.setContent {
            if (open.value) WorkspaceSubtoolsPanel(EditorTool.PEN, brushes, active.value,
                AppSettings(language = UiLanguage.ENGLISH), { active.value = it.id }, {}, {}, {}, {}, Modifier.width(280.dp).height(220.dp))
        }
        compose.onNode(hasText("Test pen 72") and isSelected()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Subtool list").performScrollToIndex(0)
        compose.onNodeWithText("Test pen 0").assertIsDisplayed()
        compose.onNodeWithText("Test pen 72").assertDoesNotExist()
        compose.runOnIdle { active.value = "test-79" }
        compose.onNode(hasText("Test pen 79") and isSelected()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Subtool list").performScrollToIndex(0)
        compose.runOnIdle { open.value = false }
        compose.runOnIdle { open.value = true }
        compose.onNode(hasText("Test pen 79") and isSelected()).assertIsDisplayed()
    }

    @Test fun switchingToolFamiliesRevealsTheirActiveVariantInSmallPanels() {
        val tool = mutableStateOf(EditorTool.GRADIENT)
        compose.setContent {
            WorkspaceSubtoolsPanel(tool.value, emptyList(), null,
                AppSettings(language = UiLanguage.ENGLISH, gradientKind = app.inkloom.document.GradientKind.DIAMOND,
                    selectionShape = SelectionShape.RECTANGLE), {}, {}, {}, {}, {}, Modifier.width(240.dp).height(120.dp))
        }
        compose.onNode(hasText("Diamond") and isSelected()).assertIsDisplayed()
        compose.runOnIdle { tool.value = EditorTool.SELECT }
        compose.onNode(hasText("Rectangle") and isSelected()).assertIsDisplayed()
    }

    @Test fun palettesRevealSelectedSwatchAndActiveSetWithVisibleLightColorMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sets = (0..39).map { index -> NamedColorSet("set-$index", "Test set $index",
            (0..299).map { ColorSetSwatch("tile-$index-$it", 0xffffffff.toInt()) }, "tile-$index-270") }
        ColorSetLibraryStore(context).update("Saved colors") { ColorSetLibrary(sets, "set-39") }
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
            ColorSetPanel(0xff000000.toInt(), UiLanguage.ENGLISH, 300.dp, {}, {})
        } } }
        val selected = compose.onNodeWithContentDescription("Color tile 271")
        selected.assertIsDisplayed().assertIsSelected()
        val pixels = selected.captureToImage().toPixelMap()
        var darkPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.red < .1f && pixel.green < .1f && pixel.blue < .1f) darkPixels++
        }
        assertTrue("White selected tile must have a dark selection marker", darkPixels > 10)
        compose.onNodeWithContentDescription("Choose color set").performClick()
        compose.onNode(hasText("Test set 39") and isSelected()).assertIsDisplayed()
    }
}
