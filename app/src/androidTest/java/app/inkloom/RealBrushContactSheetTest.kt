package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.importer.AbrBrushParser
import app.inkloom.importer.SutBrushParser
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealBrushContactSheetTest {
    @Test fun stagedSutAndAbrRenderDistinctProductionRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val staged = File("/data/local/tmp/inkloom-real-brushes")
        val sut = firstUsable(staged, "sut", BrushFormat.SUT)
        val abr = firstUsable(staged, "abr", BrushFormat.ABR)
        assumeTrue("LOCAL_FIXTURE_REQUIRED: stage SUT and ABR under $staged", sut != null && abr != null)
        val surface = PixelTileSurface(1200, 900)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 900)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }
        fun render(brush: ImportedBrush, row: Float) {
            instrumentation.runOnMainSync {
                view.applyBrushConfiguration(
                    EditorTool.PEN, AppSettings(), brush, brush.defaultSize.coerceIn(30f, 90f),
                    1f, Color.BLACK, EraserPreset.NORMAL_FREE,
                )
                view.prepareBrushAssetsNow()
                stroke(view, row)
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
        }
        render(requireNotNull(sut), .34f)
        val sutFingerprint = requireNotNull(view.lastBrushExecutionFingerprint)
        render(requireNotNull(abr), .68f)
        val abrFingerprint = requireNotNull(view.lastBrushExecutionFingerprint)
        assertNotEquals(sutFingerprint.compiledProgramHash, abrFingerprint.compiledProgramHash)
        assertTrue(sutFingerprint.committedTileHash.isNotBlank() && abrFingerprint.committedTileHash.isNotBlank())

        val snapshot = surface.snapshot()
        val pixels = snapshot.copyPremultipliedPixels(PixelRect(0, 0, 1200, 900))
        val artifact = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            .resolve("sut-abr-production-raster.png")
        Bitmap.createBitmap(pixels, 1200, 900, Bitmap.Config.ARGB_8888).also { bitmap ->
            try { artifact.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "cp ${artifact.absolutePath} /data/local/tmp/inkloom-sut-abr-production-raster.png",
            ),
        ).use { it.readBytes() }
    }

    private fun firstUsable(root: File, extension: String, format: BrushFormat): ImportedBrush? {
        val assetRoot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "contact-$extension")
        return root.listFiles().orEmpty().filter { it.isFile && it.extension.equals(extension, true) }
            .sortedBy(File::getName).asSequence().flatMap { source ->
                val fallback = ImportedBrush(
                    name = source.nameWithoutExtension, format = format, compatibility = Compatibility.BASIC,
                    sizeBytes = source.length(), report = emptyList(),
                )
                when (format) {
                    BrushFormat.SUT -> sequenceOf(SutBrushParser(File(assetRoot, source.name)).enrich(source, source.name, fallback))
                    BrushFormat.ABR -> AbrBrushParser(File(assetRoot, source.name)).enrich(source, source.name, fallback).asSequence()
                    else -> emptySequence()
                }
            }.map(BrushPresetV5Import::convertOrReject).firstOrNull { it.v5PresetJson != null }
    }

    private fun stroke(view: InkCanvasView, row: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, time: Long, pressure: Float) = MotionEvent.obtain(
            1L, time, action, 1, properties,
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y; this.pressure = pressure; size = .02f
                setAxisValue(MotionEvent.AXIS_TILT, .45f)
            }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        val y = view.height * row
        listOf(
            event(MotionEvent.ACTION_DOWN, 180f, y, 1L, .2f),
            event(MotionEvent.ACTION_MOVE, 440f, y - 18f, 9L, .5f),
            event(MotionEvent.ACTION_MOVE, 700f, y + 15f, 17L, .75f),
            event(MotionEvent.ACTION_UP, 990f, y, 25L, 1f),
        ).forEach { event -> try { view.dispatchTouchEvent(event) } finally { event.recycle() } }
    }
}
