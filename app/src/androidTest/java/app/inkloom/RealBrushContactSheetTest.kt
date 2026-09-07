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
                behaviorPattern(view, row)
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
            // Publication and its diagnostic hash use different workers. Wait for this preset's
            // fingerprint instead of reading null (or the previous row) immediately after publish.
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (view.lastBrushExecutionFingerprint?.presetId != brush.id && android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(10)
            }
            org.junit.Assert.assertEquals(brush.id, view.lastBrushExecutionFingerprint?.presetId)
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

    private fun behaviorPattern(view: InkCanvasView, row: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, time: Long, pressure: Float, tilt: Float) = MotionEvent.obtain(
            1L, time, action, 1, properties,
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y; this.pressure = pressure; size = .02f
                setAxisValue(MotionEvent.AXIS_TILT, tilt)
            }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        fun stroke(points: List<FloatArray>) {
            points.forEachIndexed { index, point ->
                val action = when (index) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                event(action, point[0], point[1], point[2].toLong(), point[3], point[4]).also { motion ->
                    try { view.dispatchTouchEvent(motion) } finally { motion.recycle() }
                }
            }
        }
        val w = view.width.toFloat()
        val y = view.height * row
        stroke(listOf(
            floatArrayOf(w * .08f, y, 0f, .65f, .10f), floatArrayOf(w * .085f, y, 4f, .65f, .10f),
        ))
        stroke(listOf(
            floatArrayOf(w * .14f, y, 10f, .12f, .12f), floatArrayOf(w * .24f, y - 10f, 18f, .35f, .12f),
            floatArrayOf(w * .34f, y + 8f, 26f, .68f, .12f), floatArrayOf(w * .44f, y, 34f, 1f, .12f),
        ))
        stroke(listOf(
            floatArrayOf(w * .49f, y, 40f, .72f, .02f), floatArrayOf(w * .58f, y - 10f, 48f, .72f, .28f),
            floatArrayOf(w * .67f, y + 8f, 56f, .72f, .55f), floatArrayOf(w * .76f, y, 64f, .72f, .82f),
        ))
        repeat(2) { pass ->
            val offset = pass * 5f
            stroke(listOf(
                floatArrayOf(w * .80f, y - 10f + offset, 70f + pass * 18f, .58f, .40f),
                floatArrayOf(w * .86f, y + 10f + offset, 78f + pass * 18f, .58f, .40f),
                floatArrayOf(w * .92f, y - 4f + offset, 86f + pass * 18f, .58f, .40f),
            ))
        }
    }
}
