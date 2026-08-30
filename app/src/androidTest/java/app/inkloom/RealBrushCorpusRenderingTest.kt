package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.AbrBrushParser
import app.inkloom.importer.ProcreateBrushParser
import app.inkloom.importer.SutBrushParser
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolver
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolution
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticExecutionDisposition
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.engine.v5.material.AndroidBrushTipMaskDecoder
import app.inkloom.engine.v5.material.BrushTipMaskRequest
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealBrushCorpusRenderingTest {
    @Test
    fun userProvidedProcreateBrushesReachATerminalProductionOutcome() =
        verifyRealBrush("brush", BrushFormat.PROCREATE, requireRendered = false)

    @Test
    fun userProvidedProcreateBrushSetBrushesReachTerminalProductionOutcomes() =
        verifyRealBrush("brushset", BrushFormat.PROCREATE, requireRendered = false)

    @Test
    fun userProvidedAbrBrushCommitsAndRendersInk() = verifyRealBrush("abr", BrushFormat.ABR)

    @Test
    fun userProvidedSutBrushKeepsRealtimePreview() = verifyRealBrush("sut", BrushFormat.SUT, verifyRealtime = true)

    private fun verifyRealBrush(
        extension: String,
        format: BrushFormat,
        verifyRealtime: Boolean = false,
        requireRendered: Boolean = true,
    ) {
        val sources = stagedSources(extension)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "real-brush-assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            var discoveredCount = 0
            var renderedCount = 0
            var rejectedCount = 0
            sources.forEachIndexed { index, source ->
                val parsed = parse(source, format, File(assetRoot, "source-$index"))
                assertNotNull("No $format brush parsed from ${source.name}", parsed.firstOrNull())
                discoveredCount += parsed.size
                parsed.forEach { decoded ->
                    val representative = BrushPresetV5Import.convertOrReject(decoded)
                    assertTrue(
                        "$format ${source.name} fell back instead of parsing: ${representative.report}",
                        representative.report.none { it.contains("parsing failed", ignoreCase = true) },
                    )
                    val nonExecuted = representative.semanticMappings.filter {
                        it.executionDisposition != SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED
                    }
                    nonExecuted.forEach { mapping ->
                        assertTrue(
                            "${representative.name} silently retained ${mapping.sourceSemantic} without a compatibility report",
                            representative.report.any {
                                it.startsWith("SEMANTIC_CAPABILITY:${mapping.executionDisposition.name}:${mapping.sourceSemantic}:")
                            },
                        )
                    }
                    if (representative.compatibility == Compatibility.FAILED) {
                        assertEquals(null, representative.v5PresetJson)
                        assertTrue(
                            "$format ${source.name} import rejection must carry a stable explicit reason",
                            representative.report.any { it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") },
                        )
                        Log.w(
                            "InkloomBrushCorpus",
                            "IMPORT_REJECTED source=${source.name} id=${representative.id} reasons=${representative.report}",
                        )
                        rejectedCount += 1
                        return@forEach
                    }
                    assertTrue(
                        "${representative.name} entered the usable library with non-executed semantics: $nonExecuted",
                        nonExecuted.isEmpty(),
                    )
                    val preset = BrushPresetV5Import.requirePreset(representative)
                    Log.i(
                        "InkloomBrushCorpus",
                        "source=${source.name} id=${representative.id} format=${representative.format} " +
                            "compatibility=${representative.compatibility} family=${preset.identity.family} " +
                            "size=${representative.defaultSize} opacity=${representative.defaultOpacity} flow=${representative.flow} " +
                            "spacing=${representative.spacing} tips=${representative.tipPreviewPaths.size} " +
                            "grain=${representative.grainDensity} pressure=${preset.dynamics.pressure} tilt=${preset.dynamics.tilt} wet=${preset.wetMix} " +
                            "output=${preset.output} smoothing=${preset.stroke?.smoothing} " +
                            "tipAspect=${preset.tip.aspect} tipRotation=${preset.tip.rotationDegrees} hardness=${preset.tip.hardness} " +
                            "tipMax=${preset.tipTexture?.assetPaths.orEmpty().map { path -> AndroidBrushTipMaskDecoder.decode(BrushTipMaskRequest(path, preset.tip.hardness))?.maximumCoverage }} " +
                            "unsupported=${representative.report.filter { it.contains("unsupported", ignoreCase = true) }} " +
                            "semanticOutcomes=${representative.semanticMappings.groupingBy { it.executionDisposition }.eachCount()} " +
                            "report=${representative.report}",
                    )
                    when (val capability = BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(preset))) {
                        BrushExecutionCapabilityResolution.Supported -> Log.i(
                            "InkloomBrushCorpus",
                            "CAPABILITY_RESOLVED id=${representative.id} outcome=SUPPORTED",
                        )
                        is BrushExecutionCapabilityResolution.Unsupported -> Log.i(
                            "InkloomBrushCorpus",
                            "CAPABILITY_RESOLVED id=${representative.id} outcome=UNSUPPORTED reasons=${capability.reasons.joinToString(",")}",
                        )
                    }
                    assertEquals(format, representative.format)
                    if (brushCommitsAndRenders(representative)) {
                        renderedCount += 1
                        if (verifyRealtime) assertSutBrushKeepsRealTimePreview(representative)
                    } else {
                        rejectedCount += 1
                    }
                }
            }
            assertEquals(
                "$format .$extension corpus terminal outcomes must conserve every discovered brush",
                discoveredCount,
                renderedCount + rejectedCount,
            )
            Log.i(
                "InkloomBrushCorpus",
                "CORPUS_SUMMARY format=$format extension=$extension filesScanned=${sources.size} " +
                    "brushesDiscovered=$discoveredCount rendered=$renderedCount rejected=$rejectedCount",
            )
            if (requireRendered) {
                assertTrue("No staged .$extension $format brush rendered through Paint Engine V5", renderedCount > 0)
            }
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    private fun stagedSources(extension: String): List<File> {
        val sourceRoot = File("/data/local/tmp/inkloom-real-brushes")
        val sources = sourceRoot.listFiles()?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.sortedBy(File::getName)
            .orEmpty()
        assumeTrue(
            "LOCAL_FIXTURE_REQUIRED: stage a .$extension brush under $sourceRoot",
            sources.isNotEmpty(),
        )
        return sources
    }

    private fun parse(source: File, format: BrushFormat, assetRoot: File): List<ImportedBrush> {
        val fallback = ImportedBrush(
            id = "corpus-${format.name.lowercase()}-${source.nameWithoutExtension.lowercase()}",
            name = source.nameWithoutExtension,
            format = format,
            compatibility = Compatibility.BASIC,
            sizeBytes = source.length(),
            report = emptyList(),
        )
        return when (format) {
            BrushFormat.PROCREATE -> ProcreateBrushParser(File(assetRoot, "procreate")).enrich(source, source.name.hashCode().toString(), fallback)
            BrushFormat.SUT -> listOf(SutBrushParser(File(assetRoot, "sut")).enrich(source, source.name.hashCode().toString(), fallback))
            BrushFormat.ABR -> AbrBrushParser(File(assetRoot, "abr")).enrich(source, source.name.hashCode().toString(), fallback)
            else -> error("Unsupported real corpus format $format")
        }
    }

    private fun assertSutBrushKeepsRealTimePreview(brush: ImportedBrush) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val surface = PixelTileSurface(1000, 1000)
        var committed: RasterLayerPatch? = null
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1000, 1000)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize.coerceIn(20f, 140f), 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            prepareBrushAssetsNow()
        }
        try {
            val frameTimes = ArrayList<Long>(90)
            instrumentation.runOnMainSync {
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 120f, 620f, 100L))
                repeat(90) { index ->
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 128f + index * 8f, 620f + (index % 7) * 3f, 110L + index))
                    view.awaitV5StrokeWorkerIdleForTest()
                    bitmap.eraseColor(Color.TRANSPARENT)
                    view.draw(Canvas(bitmap))
                    if (index >= 8) frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                }
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync {
                bitmap.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(bitmap))
                assertTrue("Real SUT ${brush.name} must present pixels before pen-up", bitmap.hasVisibleAlpha())
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 860f, 640f, 220L))
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
            val sorted = frameTimes.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            Log.i(
                "InkloomV5Perf",
                "real_sut=${brush.name} p95_ms=$p95Ms size=${brush.defaultSize} spacing=${brush.spacing} " +
                    "spacing_mode=${brush.spacingMode} flow=${brush.flow} tips=${brush.tipPreviewPaths.size} " +
                    "grain=${brush.grainDensity} dual=${brush.dualTipPreviewPaths.size}",
            )
            assertTrue("Real SUT ${brush.name} preview p95 was ${p95Ms}ms", p95Ms < 16.7f)
            requireNotNull(committed).close()
        } finally {
            bitmap.recycle()
        }
    }

    private fun brushCommitsAndRenders(brush: ImportedBrush): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1000, 1000)
        var committed: RasterLayerPatch? = null
        var rejection: String? = null
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1000, 1000)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
            onPaintCapabilityRejected = { rejection = it }
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize.coerceAtLeast(20f), 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            prepareBrushAssetsNow()
        }
        instrumentation.runOnMainSync {
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 500f, 1L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 500f, 500f, 2L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 750f, 500f, 3L))
        }
        view.awaitV5StrokeWorkerIdleForTest()
        instrumentation.runOnMainSync { }
        val patch = committed
        val terminalOutcomeCount = (if (patch != null) 1 else 0) + (if (rejection != null) 1 else 0)
        assertEquals(
            "${brush.format} ${brush.name} must end as exactly one of rendered/committed or explicitly rejected; " +
                "committed=${patch != null} rejection=$rejection",
            1,
            terminalOutcomeCount,
        )
        if (patch == null) {
            assertTrue(
                "${brush.format} ${brush.name} rejection must carry a stable explicit reason: $rejection",
                rejection?.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") == true,
            )
            Log.w(
                "InkloomBrushCorpus",
                "PRODUCTION_REJECTED id=${brush.id} format=${brush.format} name=${brush.name} rejection=$rejection",
            )
            return false
        }
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val foundInk = bitmap.hasVisibleAlpha() && surface.tileCount > 0
        bitmap.recycle()
        patch.close()
        assertTrue("${brush.format} ${brush.name} committed but rendered no ink", foundInk)
        Log.i("InkloomBrushCorpus", "PRODUCTION_RENDERED id=${brush.id} format=${brush.format} name=${brush.name}")
        return true
    }

    private fun Bitmap.hasVisibleAlpha(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { Color.alpha(it) > 0 }
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .1f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }
}
