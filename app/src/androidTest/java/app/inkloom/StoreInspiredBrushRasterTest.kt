package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The line-art pens, pencils and crayons draw through the production engine with their own character. */
@RunWith(AndroidJUnit4::class)
class StoreInspiredBrushRasterTest {
    private val ids = listOf("system-clean-liner", "system-ink-g-pen", "system-cream-pencil", "system-lineart-pencil",
        "system-wax-crayon", "system-soft-pastel", "system-wet-wash", "system-watercolor-edge", "system-triangle-watercolor",
        "system-square-watercolor", "system-gouache", "system-flat-gouache")

    @Test fun eachBrushDrawsWithItsOwnCharacter() {
        val strokes = ids.associateWith { render(requireNotNull(SystemBrushCatalog.byId(it))) }
        saveSheet(strokes)
        strokes.forEach { (id, pixels) -> android.util.Log.i("InkloomStoreBrushes", "$id covered=${pixels.count { it ushr 24 > 0 }}") }
        strokes.forEach { (id, pixels) -> assertTrue("$id draws", pixels.count { it ushr 24 > 0 } > 400) }

        // Pens taper through pressure: thin where the stroke lands, full where it presses hardest.
        listOf("system-clean-liner", "system-ink-g-pen").forEach { id ->
            val pixels = strokes.getValue(id)
            val start = thickness(pixels, 150); val middle = thickness(pixels, 300)
            assertTrue("$id tapers in: start $start, middle $middle", start * 2 <= middle)
        }
        // Crayons skip on the paper: many partly covered pixels inside the stroke, unlike a pen.
        fun broken(id: String) = strokes.getValue(id).let { p -> p.count { (it ushr 24) in 1..170 }.toFloat() / p.count { it ushr 24 > 0 } }
        android.util.Log.i("InkloomStoreBrushes", ids.joinToString { "$it broken=${broken(it)}" })
        assertTrue("the crayon is broken up: ${broken("system-wax-crayon")}", broken("system-wax-crayon") > broken("system-ink-g-pen") + .15f)
        assertTrue("the pastel is broken up: ${broken("system-soft-pastel")}", broken("system-soft-pastel") > broken("system-ink-g-pen") + .15f)
        // The watercolour edge dries darker at its edges than in its middle, across the stroke where it runs flat.
        val edge = strokes.getValue("system-watercolor-edge")
        val across = (0 until HEIGHT).map { y -> edge[y * WIDTH + 220] ushr 24 }.filter { it > 8 }
        assertTrue("watercolour edge has a body: ${across.size}", across.size > 10)
        val rim = maxOf(across.take(4).max(), across.takeLast(4).max())
        val middle = across[across.size / 2]
        assertTrue("edges darker than the middle: rim $rim middle $middle", rim > middle * 1.15f)
        // Triangle and square dabs shift hue from dab to dab.
        listOf("system-triangle-watercolor", "system-square-watercolor").forEach { id ->
            val opaque = strokes.getValue(id).filter { it ushr 24 > 200 }.map { it and 0xffffff }.distinct()
            android.util.Log.i("InkloomStoreBrushes", "$id colours=${opaque.size}")
        }
    }

    /** Optional: real CSP ribbon brushes pushed to /data/local/tmp/inkloom-ribbon-samples render as unbroken ribbons. */
    @Test fun cspRibbonSamplesDraw() {
        val samples = java.io.File("/data/local/tmp/inkloom-ribbon-samples").listFiles { file -> file.extension.equals("sut", true) }
            ?.sortedBy { it.name }.orEmpty()
        if (samples.isEmpty()) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = java.io.File(context.cacheDir, "ribbon-assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            val parser = app.inkloom.importer.SutBrushParser(assets)
            val strokes = samples.associate { source ->
                val decoded = parser.enrich(source, source.name.hashCode().toUInt().toString(16), ImportedBrush(
                    name = source.nameWithoutExtension, format = app.inkloom.model.BrushFormat.SUT,
                    compatibility = app.inkloom.model.Compatibility.EXTRACTED, sizeBytes = source.length(), report = emptyList()))
                val brush = app.inkloom.brushpresetv5.BrushPresetV5Import.convertDecodedSource(decoded)
                assertTrue("${source.name} keeps its ribbon", app.inkloom.brushpresetv5.BrushPresetV5Import.requirePreset(brush).stamp?.ribbon == true)
                source.nameWithoutExtension to render(brush)
            }
            strokes.forEach { (name, pixels) -> assertTrue("$name draws", pixels.any { it ushr 24 > 0 }) }
            saveSheet(strokes, "ribbon-samples")
        } finally { assets.deleteRecursively() }
    }

    /** Covered pixels in column [x]. */
    private fun thickness(pixels: IntArray, x: Int) = (0 until HEIGHT).count { y -> pixels[y * WIDTH + x] ushr 24 > 96 }

    private fun render(brush: ImportedBrush): IntArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(WIDTH, HEIGHT)
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, WIDTH, HEIGHT)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { }
                applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize * 2.5f, 1f, 0xff3a6fc8.toInt(), EraserPreset.NORMAL_FREE)
            }
            // A gentle S curve with a pressure swell: light, heavy in the middle, light again.
            val steps = 40
            view.send(MotionEvent.ACTION_DOWN, 140f, 120f, 10L, .05f)
            for (index in 1..steps) {
                val t = index / steps.toFloat()
                view.send(MotionEvent.ACTION_MOVE, 140f + t * 320f, 120f + 30f * kotlin.math.sin(t * 6.2831855f),
                    10L + index * 9L, (.05f + .9f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(.02f, 1f))
            }
            view.send(MotionEvent.ACTION_UP, 460f, 120f, 10L + (steps + 1) * 9L, .02f)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        return surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, WIDTH, HEIGHT))
    }

    private fun saveSheet(strokes: Map<String, IntArray>, name: String = "store-brushes") {
        val sheet = Bitmap.createBitmap(WIDTH, (HEIGHT + 24) * strokes.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
        strokes.entries.forEachIndexed { row, (id, pixels) ->
            val top = row * (HEIGHT + 24f)
            canvas.drawText(id, 10f, top + 18f, label)
            // The engine hands back premultiplied pixels; a Bitmap built from an IntArray expects straight ones.
            val straight = IntArray(pixels.size) { index ->
                val p = pixels[index]; val a = p ushr 24
                if (a == 0 || a == 255) p else (a shl 24) or ((p ushr 16 and 255) * 255 / a shl 16) or
                    ((p ushr 8 and 255) * 255 / a shl 8) or ((p and 255) * 255 / a)
            }
            val tile = Bitmap.createBitmap(straight, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            try { canvas.drawBitmap(tile, 0f, top + 24f, null) } finally { tile.recycle() }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)).resolve("$name.png")
        try { output.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { sheet.recycle() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cp ${output.absolutePath} /data/local/tmp/inkloom-$name.png")).use { it.readBytes() }
    }

    private fun InkCanvasView.send(action: Int, x: Float, y: Float, time: Long, pressure: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; this.pressure = pressure; size = .01f
        })
        MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            .also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }

    private companion object {
        const val WIDTH = 600
        const val HEIGHT = 240
    }
}
