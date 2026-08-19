package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv4.BrushPresetV4
import app.inkloom.brushpresetv4.BrushFamily
import app.inkloom.brushpresetv4.CompiledBrushProgram
import app.inkloom.brushpresetv4.DepositionSettings
import app.inkloom.brushpresetv4.DynamicsSettings
import app.inkloom.brushpresetv4.EngineOptions
import app.inkloom.brushpresetv4.OwnedBrushPreset
import app.inkloom.brushpresetv4.PaperGrainSettings
import app.inkloom.brushpresetv4.PresetIdentity
import app.inkloom.brushpresetv4.PresetOwnership
import app.inkloom.brushpresetv4.PresetOwnershipOperations
import app.inkloom.brushpresetv4.PressureMappings
import app.inkloom.brushpresetv4.SpacingMode
import app.inkloom.brushpresetv4.SpacingSettings
import app.inkloom.brushpresetv4.StrokeSettings
import app.inkloom.brushpresetv4.TiltMappings
import app.inkloom.brushpresetv4.TipSettings
import app.inkloom.brushpresetv4.TipTextureSettings
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BRUSH_PRESET_V4_FOUNDATION raster parity: the V4 schema -> compile -> InkStroke path must render
 * identically to the legacy (V3) reference InkStroke for the two reference families. This is the
 * "preset model migration must not alter raster output" gate.
 */
@RunWith(AndroidJUnit4::class)
class BrushPresetV4ParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewportWidth = 900
    private val viewportHeight = 900

    // Legacy (V3) reference strokes, identical to ReferencePresetGoldenTest.
    private fun v3Ink(points: List<StrokePoint>) = InkStroke(
        id = "system-pen", brushId = "system-pen", points = points,
        width = 5f, spacing = .12f, sizePressureEnabled = true, sizePressureMinimum = .35f, colorArgb = 0xFF000000,
    )

    private fun v3Pencil(points: List<StrokePoint>) = InkStroke(
        id = "system-hb-pencil", brushId = "system-hb-pencil", points = points,
        width = 24f, spacing = .26f, flow = .68f, sizePressureEnabled = true, sizePressureMinimum = .3f,
        opacityPressureEnabled = true, opacityPressureMinimum = .2f, colorArgb = 0xFF000000,
    )

    // V4 reference presets mirror the same semantics.
    private fun v4Ink(points: List<StrokePoint>) = CompiledBrushProgram.compile(
        BrushPresetV4(
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
        ),
    ).toInkStroke(0xFF000000, points)

    private fun v4Pencil(points: List<StrokePoint>) = CompiledBrushProgram.compile(
        BrushPresetV4(
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
                // V3 golden pencil has no tilt/grain channels; the schema transform must not add them.
                tilt = TiltMappings(),
            ),
            paperGrain = null,
            tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
            stroke = StrokeSettings(),
            engineOptions = EngineOptions(),
        ),
    ).toInkStroke(0xFF000000, points)

    /** V4 pencil with the new V4 semantic channels enabled (tilt->width, tilt->grain, paper grain). */
    private fun v4PencilWithV4Semantics(points: List<StrokePoint>) = CompiledBrushProgram.compile(
        BrushPresetV4(
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
        ),
    ).toInkStroke(0xFF000000, points)

    private fun straight() = (0..300).map { index -> StrokePoint(.1f + index * .0025f, .5f, pressure = .6f, timeMs = (index * 2L).toLong()) }

    @Test
    fun v4InkRendersIdenticallyToV3Ink() {
        val v3 = raster(v3Ink(straight()))
        val v4 = raster(v4Ink(straight()))
        try {
            assertEquals("V4 Ink must render byte-identically to V3 Ink", signature(v3), signature(v4))
        } finally {
            v3.recycle()
            v4.recycle()
        }
    }

    @Test
    fun v4PencilRendersIdenticallyToV3Pencil() {
        val v3 = raster(v3Pencil(straight()))
        val v4 = raster(v4Pencil(straight()))
        try {
            assertEquals("V4 Pencil must render byte-identically to V3 Pencil", signature(v3), signature(v4))
        } finally {
            v3.recycle()
            v4.recycle()
        }
    }

    @Test
    fun v4ReferenceFamiliesStillMeetStructuralGoldenInvariants() {
        val v4P = raster(v4Pencil(straight()))
        val v4PSem = raster(v4PencilWithV4Semantics(straight()))
        val v4I = raster(v4Ink(straight()))
        try {
            // Pencil must stay hole-free and textured; Ink must stay continuous and low-scalloping.
            val pencilStats = scalloping(v4Pencil(straight()))
            assertTrue("V4 Pencil must have no geometric holes (gaps=${pencilStats.gaps})", pencilStats.gaps <= 2)
            assertTrue("V4 Pencil must show texture (pump=${pencilStats.pump})", pencilStats.pump > .05f)
            // Enabling the V4 semantic channels must change the raster (they are real semantics).
            val v4PSemStats = scalloping(v4PencilWithV4Semantics(straight()))
            assertTrue("V4 semantics must not break hole-free rendering (gaps=${v4PSemStats.gaps})", v4PSemStats.gaps <= 2)
            assertTrue(
                "V4 tilt->width / tilt->grain / paper grain must alter the raster",
                signature(v4P) != signature(v4PSem),
            )
            val inkStats = scalloping(v4Ink(straight()))
            assertTrue("V4 Ink must have no geometric holes (gaps=${inkStats.gaps})", inkStats.gaps <= 2)
            assertTrue("V4 Ink must stay low scalloping (pump=${inkStats.pump})", inkStats.pump < .6f)
        } finally {
            v4P.recycle()
            v4PSem.recycle()
            v4I.recycle()
        }
    }

    @Test
    fun duplicatePresetRendersByteIdenticallyToSource() {
        val source = OwnedBrushPreset(referencePencilPreset(), PresetOwnership.Factory("system-hb-pencil"))
        val duplicate = PresetOwnershipOperations.duplicate(source)
        val saveAs = PresetOwnershipOperations.saveAs(source, "HB Pencil - Custom")
        val src = raster(CompiledBrushProgram.compile(source.preset).toInkStroke(0xFF000000, straight()))
        val dup = raster(CompiledBrushProgram.compile(duplicate.preset).toInkStroke(0xFF000000, straight()))
        val saved = raster(CompiledBrushProgram.compile(saveAs.preset).toInkStroke(0xFF000000, straight()))
        try {
            assertNotEquals("A duplicate must get a new identity id", source.id, duplicate.id)
            assertEquals("renderBrushId must survive duplication", source.preset.identity.renderBrushId, duplicate.preset.identity.renderBrushId)
            assertEquals("renderSeed must survive duplication", source.preset.renderSeed, duplicate.preset.renderSeed)
            assertEquals("Duplicate must render byte-identically to the source", signature(src), signature(dup))
            assertEquals("Save As must render byte-identically to the source", signature(src), signature(saved))
        } finally {
            src.recycle()
            dup.recycle()
            saved.recycle()
        }
    }

    @Test
    fun randomizedSeedProducesDifferentDeterministicRaster() {
        val user = PresetOwnershipOperations.duplicate(
            OwnedBrushPreset(referencePencilPreset(), PresetOwnership.Factory("system-hb-pencil")),
        )
        val randomized = PresetOwnershipOperations.randomizeRenderSeed(user, 12345)
        val before = raster(CompiledBrushProgram.compile(user.preset).toInkStroke(0xFF000000, straight()))
        val after = raster(CompiledBrushProgram.compile(randomized.preset).toInkStroke(0xFF000000, straight()))
        try {
            assertTrue("Explicit seed randomization must change the deterministic raster", signature(before) != signature(after))
            assertEquals("Seed change must not touch renderBrushId", user.preset.identity.renderBrushId, randomized.preset.identity.renderBrushId)
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    private fun referencePencilPreset(): BrushPresetV4 = BrushPresetV4(
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
            tilt = TiltMappings(),
        ),
        paperGrain = null,
        tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
        stroke = StrokeSettings(),
        engineOptions = EngineOptions(),
    )

    private fun raster(stroke: InkStroke): Bitmap {
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, viewportWidth, viewportHeight) }
            bitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "v4-parity")
            view.draw(Canvas(bitmap))
        }
        return bitmap!!
    }

    private data class Stats(val coverageMean: Double, val pump: Float, val crossings: Int, val gaps: Int)

    private fun scalloping(stroke: InkStroke): Stats {
        val bitmap = raster(stroke)
        val profile = crossSectionAlpha(bitmap, stroke)
        bitmap.recycle()
        if (profile.isEmpty()) return Stats(0.0, 0f, 0, profile.size)
        val mean = profile.average()
        if (mean <= .0001) return Stats(mean, 0f, 0, profile.count { it <= 4 })
        val smoothed = IntArray(profile.size) { index ->
            val from = (index - 4).coerceAtLeast(0)
            val to = (index + 4).coerceAtMost(profile.lastIndex)
            (from..to).map { profile[it] }.average().toInt()
        }
        val deviations = profile.indices.map { abs(profile[it] - smoothed[it]) }
        val pump = (deviations.maxOrNull() ?: 0) / mean.toFloat()
        val crossings = (1 until profile.size).count { index ->
            (profile[index] - smoothed[index] > 0) != (profile[index - 1] - smoothed[index - 1] > 0)
        }
        return Stats(mean, pump, crossings, profile.count { it <= 4 })
    }

    private fun crossSectionAlpha(bitmap: Bitmap, stroke: InkStroke): IntArray {
        val points = stroke.points
        if (points.size < 2) return IntArray(0)
        val arc = FloatArray(points.size)
        for (index in 1 until points.size) {
            arc[index] = arc[index - 1] + hypot(
                ((points[index].x - points[index - 1].x) * viewportWidth).toDouble(),
                ((points[index].y - points[index - 1].y) * viewportHeight).toDouble(),
            ).toFloat()
        }
        val total = arc.last()
        if (total <= .0001f) return IntArray(0)
        val maxRadius = (stroke.width * .8f).toInt().coerceAtLeast(8)
        val samples = (total / 2f).toInt().coerceAtLeast(2)
        val result = IntArray(samples)
        var arcIndex = 0
        for (sample in 0 until samples) {
            val target = total * sample / (samples - 1)
            while (arcIndex < points.lastIndex - 1 && arc[arcIndex + 1] < target) arcIndex++
            val point = points[arcIndex]
            val prev = points[(arcIndex - 1).coerceAtLeast(0)]
            val next = points[(arcIndex + 1).coerceAtMost(points.lastIndex)]
            var tx = (next.x - prev.x) * viewportWidth
            var ty = (next.y - prev.y) * viewportHeight
            val length = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(.001f)
            tx /= length
            ty /= length
            val cx = point.x * viewportWidth
            val cy = point.y * viewportHeight
            var coverage = 0
            for (offset in -maxRadius..maxRadius) {
                val px = (cx - ty * offset).toInt()
                val py = (cy + tx * offset).toInt()
                if (px in 0 until bitmap.width && py in 0 until bitmap.height) coverage += Color.alpha(bitmap.getPixel(px, py))
            }
            result[sample] = coverage
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()
}
