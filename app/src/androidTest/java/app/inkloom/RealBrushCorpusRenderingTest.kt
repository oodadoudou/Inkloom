package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.AbrBrushParser
import app.inkloom.importer.ProcreateBrushParser
import app.inkloom.importer.SutBrushParser
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealBrushCorpusRenderingTest {
    @Test
    fun userProvidedProcreateSutAndAbrPresetsCommitAndRenderInk() {
        val required = InstrumentationRegistry.getArguments().getString("requireRealBrushSamples") == "true"
        val sourceRoot = File("/data/local/tmp/inkloom-real-brushes")
        val sources = sourceRoot.listFiles()?.filter(File::isFile).orEmpty()
        if (sources.isEmpty() && !required) return
        assertTrue("Expected staged real brushes under $sourceRoot", sources.isNotEmpty())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "real-brush-assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            val parsed = sources.flatMap { source ->
                val format = when (source.extension.lowercase()) {
                    "brush", "brushset" -> BrushFormat.PROCREATE
                    "sut" -> BrushFormat.SUT
                    "abr" -> BrushFormat.ABR
                    else -> return@flatMap emptyList()
                }
                val fallback = ImportedBrush(
                    name = source.nameWithoutExtension,
                    format = format,
                    compatibility = Compatibility.BASIC,
                    sizeBytes = source.length(),
                    report = emptyList(),
                )
                when (format) {
                    BrushFormat.PROCREATE -> ProcreateBrushParser(File(assetRoot, "procreate")).enrich(source, source.name.hashCode().toString(), fallback)
                    BrushFormat.SUT -> listOf(SutBrushParser(File(assetRoot, "sut")).enrich(source, source.name.hashCode().toString(), fallback))
                    BrushFormat.ABR -> AbrBrushParser(File(assetRoot, "abr")).enrich(source, source.name.hashCode().toString(), fallback)
                    else -> emptyList()
                }
            }
            val representatives = listOf(BrushFormat.PROCREATE, BrushFormat.SUT, BrushFormat.ABR).map { format ->
                parsed.firstOrNull { it.format == format }.also { assertNotNull("No $format brush parsed from the real corpus", it) }!!
            }.map(BrushPresetV5Import::convertDecodedSource)
            representatives.forEach { brush -> assertBrushCommitsAndRenders(brush) }
            assertSutBrushKeepsRealTimePreview(representatives.first { it.format == BrushFormat.SUT })
            assertEquals(setOf(BrushFormat.PROCREATE, BrushFormat.SUT, BrushFormat.ABR), representatives.mapTo(linkedSetOf(), ImportedBrush::format))
        } finally {
            assetRoot.deleteRecursively()
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
                    view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 128f + index * 8f, 620f + (index % 7) * 3f, 110L + index))
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    bitmap.eraseColor(Color.TRANSPARENT)
                    view.draw(Canvas(bitmap))
                    if (index >= 8) frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                }
                assertTrue("Real SUT ${brush.name} must present pixels before pen-up", bitmap.hasVisibleAlpha())
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 860f, 640f, 220L))
            }
            val sorted = frameTimes.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            assertTrue("Real SUT ${brush.name} preview p95 was ${p95Ms}ms", p95Ms < 16.7f)
            requireNotNull(committed).close()
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertBrushCommitsAndRenders(brush: ImportedBrush) {
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
        val patch = requireNotNull(committed) {
            "${brush.format} ${brush.name} did not commit V5 pixels; rejection=$rejection; preset=$brush"
        }
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val foundInk = bitmap.hasVisibleAlpha() && surface.tileCount > 0
        bitmap.recycle()
        patch.close()
        assertTrue("${brush.format} ${brush.name} committed but rendered no ink", foundInk)
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
