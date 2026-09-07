package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertTrue
import app.inkloom.brushpresetv5.QuickBrushEditSession
import org.junit.Test
import org.junit.runner.RunWith

/** Quick author parameters must change real production pixels, not just serialized fields. */
@RunWith(AndroidJUnit4::class)
class QuickBrushRasterTest {
    @Test fun stabilizationDirectionHardnessAndPaperDensityChangeProductionRaster() {
        val edits = listOf(
            Triple("system-g-pen", "smoothing", .95f),
            Triple("system-6b-pencil", "rotation", 90f),
            Triple("system-hb-pencil", "hardness", .08f),
            Triple("system-hb-pencil", "paperGrainDensity", .02f),
        )
        val sheet = android.graphics.Bitmap.createBitmap(WIDTH * 2, (HEIGHT + 28) * edits.size, android.graphics.Bitmap.Config.ARGB_8888)
        val sheetCanvas = android.graphics.Canvas(sheet)
        sheetCanvas.drawColor(Color.WHITE)
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
        try {
            edits.forEachIndexed { row, (id, option, value) ->
                val source = requireNotNull(SystemBrushCatalog.byId(id))
                val edited = QuickBrushEditSession.start(source).change(option, value).commit()
                val before = render(source)
                val after = render(edited)
                val changed = before.indices.count { kotlin.math.abs((before[it] ushr 24) - (after[it] ushr 24)) >= 16 }
                assertTrue("$id / $option must visibly change the stroke: $changed pixels", changed > 32)
                assertTrue("$id / $option must execute", after.any { it ushr 24 > 0 })
                android.util.Log.i("InkloomQuickRaster", "$id $option changedAlphaPixels=$changed")
                val top = row * (HEIGHT + 28)
                sheetCanvas.drawText("$id: $option (original / edited)", 12f, top + 22f, labelPaint)
                listOf(before, after).forEachIndexed { column, pixels ->
                    val tile = android.graphics.Bitmap.createBitmap(pixels, WIDTH, HEIGHT, android.graphics.Bitmap.Config.ARGB_8888)
                    try { sheetCanvas.drawBitmap(tile, column * WIDTH.toFloat(), top + 28f, null) }
                    finally { tile.recycle() }
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val output = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)).resolve("quick-brush-raster.png")
            output.outputStream().use { sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "cp ${output.absolutePath} /data/local/tmp/inkloom-quick-brush-raster.png",
            )).use { it.readBytes() }
        } finally { sheet.recycle() }
    }

    private fun render(brush: app.inkloom.model.ImportedBrush): IntArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(WIDTH, HEIGHT)
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, WIDTH, HEIGHT)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { }
                applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            }
            view.send(MotionEvent.ACTION_DOWN, 120f, 120f, 10L)
            repeat(24) { index ->
                view.send(MotionEvent.ACTION_MOVE, 130f + index * 14f,
                    if (index % 2 == 0) 100f else 140f, 20L + index * 8L)
            }
            view.send(MotionEvent.ACTION_UP, 480f, 120f, 220L)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        return surface.snapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, WIDTH, HEIGHT))
    }

    private fun InkCanvasView.send(action: Int, x: Float, y: Float, time: Long) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = .75f; size = .01f
            setAxisValue(MotionEvent.AXIS_TILT, .55f)
        })
        MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            .also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }

    private companion object {
        const val WIDTH = 600
        const val HEIGHT = 240
    }
}
