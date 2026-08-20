package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv4.BrushFamily
import app.inkloom.brushpresetv4.BrushPresetV4
import app.inkloom.brushpresetv4.CompiledBrushProgram
import app.inkloom.brushpresetv4.DepositionSettings
import app.inkloom.brushpresetv4.DynamicsSettings
import app.inkloom.brushpresetv4.EditorWorkingCopy
import app.inkloom.brushpresetv4.EngineOptions
import app.inkloom.brushpresetv4.OwnedBrushPreset
import app.inkloom.brushpresetv4.PaperGrainSettings
import app.inkloom.brushpresetv4.PresetIdentity
import app.inkloom.brushpresetv4.PresetMigrator
import app.inkloom.brushpresetv4.PresetOwnership
import app.inkloom.brushpresetv4.PresetOwnershipOperations
import app.inkloom.brushpresetv4.PresetReverseMigrator
import app.inkloom.brushpresetv4.PressureMappings
import app.inkloom.brushpresetv4.SpacingMode
import app.inkloom.brushpresetv4.SpacingSettings
import app.inkloom.brushpresetv4.StrokeSettings
import app.inkloom.brushpresetv4.TiltMappings
import app.inkloom.brushpresetv4.TipSettings
import app.inkloom.brushpresetv4.TipTextureSettings
import app.inkloom.document.StrokePoint
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.model.BrushFormat
import app.inkloom.ui.InkCanvasView
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BRUSH_EDITOR_V1 persistence + full-chain regression:
 * - user preset saved through the production BrushMetadataStore, re-loaded (simulated restart), still compiles to the
 *   same raster as before the save;
 * - WorkingCopy -> serialize -> deserialize -> compile -> raster is byte-identical.
 */
@RunWith(AndroidJUnit4::class)
class BrushPresetV4EditorPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun userPresetSurvivesStoreRoundTripWithIdenticalRaster() {
        val factory = OwnedBrushPreset(editorPencil(), PresetOwnership.Factory("system-hb-pencil"))
        val edited = PresetOwnershipOperations.duplicate(factory)
        val editedPreset = edited.preset.copy(
            tip = edited.preset.tip.copy(size = 30f, hardness = .8f),
            deposition = edited.preset.deposition.copy(flow = .5f),
        )
        val before = raster(CompiledBrushProgram.compile(editedPreset).toInkStroke(0xFF111111L, points()))

        val preferenceName = "v4-production-persistence-${System.nanoTime()}"
        val store = BrushMetadataStore(context, preferenceName, "brushes")
        val projected = PresetReverseMigrator.toV3(editedPreset, BrushFormat.INKLOOM)
        store.save(listOf(projected))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        val loadedPreset = PresetMigrator.toV4(loaded.first())
        assertEquals("renderSeed must survive persistence", editedPreset.renderSeed, loadedPreset.renderSeed)
        assertEquals("renderBrushId must survive persistence", "system-hb-pencil", loadedPreset.identity.renderBrushId)
        assertEquals("User id must survive persistence", edited.id, loaded.first().id)
        val after = raster(CompiledBrushProgram.compile(loadedPreset).toInkStroke(0xFF111111L, points()))

        try {
            assertEquals("Saved preset must rasterize identically after reload", signature(before), signature(after))
        } finally {
            before.recycle()
            after.recycle()
            context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun workingCopySerializeDeserializeCompileRasterIsIdentical() {
        val factory = OwnedBrushPreset(editorPencil(), PresetOwnership.Factory("system-hb-pencil"))
        val session = EditorWorkingCopy.start(factory).update { it.copy(tip = it.tip.copy(size = 40f)) }

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }
        val encoded = json.encodeToString(BrushPresetV4.serializer(), session.current)
        val decoded = json.decodeFromString(BrushPresetV4.serializer(), encoded)

        val before = raster(session.toInkStroke(0xFF111111L, points()))
        val after = raster(CompiledBrushProgram.compile(decoded).toInkStroke(0xFF111111L, points()))
        try {
            assertEquals("WorkingCopy -> serialize -> deserialize -> compile must render identically", signature(before), signature(after))
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    @Test
    fun unsupportedCapabilityValueIsNeverMutated() {
        // Ink has no paper grain; the descriptor for paper density must still read its (null) block
        // as 0 without creating one, and applying it must not silently inject a paper block.
        val ink = editorInk()
        val paperDescriptor = app.inkloom.brushpresetv4.BrushOptionDescriptors.option("paperGrainDensity")
        assertTrue(!paperDescriptor.isAvailableFor(BrushFamily.INK))
        assertEquals(0f, paperDescriptor.get(ink), 1e-4f)
        // The panel never renders it (capability-driven), and even a direct apply is a no-op for an
        // unsupported capability, so a hidden value can never be accidentally mutated.
        val applied = paperDescriptor.apply(ink, .7f)
        assertTrue("Ink preset must still have no paperGrain block", applied.paperGrain == null)
        assertEquals("Ink preset must be unchanged by an unsupported option", ink, applied)
    }

    private fun points(): List<StrokePoint> = (0..120).map { i ->
        StrokePoint(.1f + i * .006f, .5f, pressure = .6f, timeMs = (i * 2L).toLong())
    }

    private fun raster(stroke: app.inkloom.document.InkStroke): Bitmap {
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, 900, 900) }
            bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "v4-persist")
            view.draw(Canvas(bitmap))
        }
        return bitmap!!
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()

    private fun editorInk(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-pen", "Reference Ink", 1, BrushFamily.INK, "system-pen"),
        renderSeed = "system-pen".hashCode(),
        tip = TipSettings(size = 5f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = 1f, buildup = 0f),
        spacing = null,
        dynamics = DynamicsSettings(
            pressure = PressureMappings(widthEnabled = true, widthMinimum = .35f, depositionEnabled = false),
            tilt = TiltMappings(),
        ),
        paperGrain = null,
        tipTexture = null,
        stroke = StrokeSettings(),
        engineOptions = EngineOptions(),
    )

    private fun editorPencil(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-hb-pencil", "Reference Pencil", 1, BrushFamily.PENCIL, "system-hb-pencil"),
        renderSeed = "system-hb-pencil".hashCode(),
        tip = TipSettings(size = 24f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = .68f, buildup = 0f),
        spacing = SpacingSettings(mode = SpacingMode.RELATIVE, value = .26f),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(
                widthEnabled = true, widthMinimum = .3f,
                depositionEnabled = true, depositionMinimum = .2f,
            ),
            tilt = TiltMappings(
                widthEnabled = true, widthStart = .2f, widthMaximum = 1f,
                grainEnabled = true, grainStart = .2f, grainMaximum = .8f,
            ),
        ),
        paperGrain = PaperGrainSettings(scale = 50f, density = .3f, strength = 1f),
        tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
        stroke = StrokeSettings(),
        engineOptions = EngineOptions(),
    )
}
