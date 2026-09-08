package app.inkloom

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.*
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BrushMaterialEditorTest {
    @Test fun importedGrainEditsChangeProductionCoverageAndPreserveSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "material-editor-grain.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0..15) for (x in 0..15) bitmap.setPixel(x, y,
            if (x < 8) 0xff000000.toInt() else 0)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val source = preset(BrushFamily.PENCIL).copy(
                tipTexture = TipTextureSettings(),
                textureGrain = TextureGrainSettings(file.path, density = 1f))
            val options = BrushOptionDescriptors.productionPanels(source).flatMap { it.second }
            val depth = options.single { it.id == "textureDepth" }
            val reverse = options.single { it.id == "textureReverseEnabled" }
            val normal = render(source)
            assertFalse("Grain depth slider must alter pixels", normal.contentEquals(render(depth.apply(source, 0f))))
            assertFalse("Invert switch must alter pixels", normal.contentEquals(render(reverse.apply(source, 1f))))
            assertEquals(1f, source.textureGrain!!.density, 0f)
            assertFalse(source.textureGrain!!.reversed)
            assertEquals(file.path, reverse.apply(source, 1f).textureGrain!!.assetPath)
        } finally { file.delete() }
    }

    @Test fun wetMaterialControlsReachPigmentDepositionAndAreNotOfferedForDryPresets() {
        val source = preset(BrushFamily.WET_PAINT).copy(wetMix = WetMixSettings(0f, 1f, 1f, 0f))
        val options = BrushOptionDescriptors.productionPanels(source).flatMap { it.second }
        val attack = options.single { it.id == "wetAttack" }
        assertFalse(render(source).contentEquals(render(attack.apply(source, .1f))))
        val dryOptions = BrushOptionDescriptors.productionPanels(preset(BrushFamily.INK)).flatMap { it.second }
        assertTrue(dryOptions.none { it.id.startsWith("wet") || it.id.startsWith("texture") })
        assertEquals(1f, source.wetMix!!.attack, 0f)
    }

    private fun preset(family: BrushFamily) = BrushPresetV5(
        identity = PresetIdentity("material", "Material", family = family, renderBrushId = "material"),
        renderSeed = 73, tip = TipSettings(150f), deposition = DepositionSettings(),
        dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()))

    private fun render(preset: BrushPresetV5): IntArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 192, 192); activeLayerId = "test"
                setRasterLayerSurfaces(mapOf("test" to PixelTileSurface(192, 192)))
                applyBrushPresetV5Configuration(preset, AppSettings())
                prepareBrushAssetsNow()
            }
        }
        val samples = (0..12).map { index -> RawDocumentSample(24f + index * 12f, 96f,
            (index + 1) * 8_000_000L, (index + 1) * 8_000_000L, .7f, 0f, 0f, InputTool.STYLUS) }
        val bitmap = view.renderIsolatedBrushPreview(samples)
        return IntArray(192 * 192).also { bitmap.getPixels(it, 0, 192, 0, 0, 192, 192); bitmap.recycle() }
    }
}
