package app.inkloom

import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.inkloom.brushpresetv5.*
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.BrushSourceEditor
import app.inkloom.ui.BrushSourceRole
import app.inkloom.ui.BrushSourceWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** B11: built-in original sources, trimming, squaring and the tiling check in the real source editor. */
class BrushSourceLibraryWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preset = BrushPresetV5(identity = PresetIdentity("source", "Source", family = BrushFamily.INK, renderBrushId = "source"), renderSeed = 1,
        tip = TipSettings(18f), deposition = DepositionSettings(), dynamics = DynamicsSettings(PressureMappings(), TiltMappings()))

    private fun edit(role: BrushSourceRole, steps: () -> Unit): BrushPresetV5 {
        var applied: BrushPresetV5? = null
        val workspace = BrushSourceWorkspace(compose.activity)
        // Paper grain needs an image tip (the stamp renderer); start that case from a built-in tip.
        val start = if (role == BrushSourceRole.GRAIN) BrushSourceWorkspace.replace(preset, BrushSourceRole.SHAPE, workspace.builtIn(BuiltInBrushSource.CHALK)) else preset
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            BrushSourceEditor(start, role, workspace, UiLanguage.ENGLISH, onDismiss = {}, onApply = { applied = it; workspace.retain(it) })
        } } }
        compose.waitForIdle()
        steps()
        compose.onNodeWithText("Apply source").performClick()
        compose.waitForIdle()
        workspace.close()
        return requireNotNull(applied)
    }
    private val busy = androidx.compose.ui.test.hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)
    /** Taps an edit, waits for its background work to finish and fails with the editor's own message if it was refused. */
    private fun choose(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodes(busy).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        val refusal = compose.onAllNodes(androidx.compose.ui.test.hasText("Unsupported", substring = true) or androidx.compose.ui.test.hasText("Could not", substring = true)
            or androidx.compose.ui.test.hasText("empty", substring = true, ignoreCase = true)).fetchSemanticsNodes()
        assertTrue("$label was refused: ${refusal.map { node -> node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null } }}", refusal.isEmpty())
    }

    @Test fun aBuiltInPaperTextureIsAppliedWithATilingCheck() {
        val result = edit(BrushSourceRole.GRAIN) {
            choose("Canvas weave")
            compose.onNodeWithContentDescription("Tiling check").performScrollTo().assertExists()
            choose("Trim to content")
        }
        val path = requireNotNull(result.textureGrain).assetPath
        BitmapFactory.decodeFile(path).also { assertEquals(512, it.width); assertEquals(512, it.height); it.recycle() }
        File(path).delete(); result.tipTexture?.assetPath?.let { File(it).delete() }
    }

    @Test fun aBuiltInTipCanBeTrimmedAndSquared() {
        val result = edit(BrushSourceRole.SHAPE) {
            choose("Bristles")
            choose("Trim to content")
            choose("Make square")
        }
        val path = result.tipTexture!!.assetPath!!
        BitmapFactory.decodeFile(path).also {
            assertEquals(it.width, it.height)
            assertTrue("trimmed below the 256 px original: ${it.width}", it.width < 256)
            it.recycle()
        }
        assertTrue(result.tipTexture!!.procedural.not())
        File(path).delete()
    }
}
