package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.diagnostics.BrushExecutionFingerprint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Black-box identity gate: visible brush cards must control the next production StrokeSession. */
class BrushUiToRasterIdentityTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val cleanRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .clear()
                    .putString("language", "ENGLISH")
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .commit()
                context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
                resetNotebookTestLibrary(context)
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(cleanRule).around(composeRule)

    @Test fun visibleCardsSelectDistinctProgramsAndRasterThroughTheRealEditor() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        val view = waitForCanvas()

        val gPen = selectAndDraw(view, group = "Pen", brush = "G Pen", expectedId = "system-g-pen", row = .28f)
        val pencil = selectAndDraw(view, group = "Pencil", brush = "HB Pencil", expectedId = "system-hb-pencil", row = .50f)
        val painting = selectAndDraw(view, group = "Brush", brush = "Dry Painting Brush", expectedId = "system-dry-painting-brush", row = .72f)

        assertEquals("system-g-pen", gPen.presetId)
        assertEquals("system-hb-pencil", pencil.presetId)
        assertEquals("system-dry-painting-brush", painting.presetId)
        assertNotEquals(gPen.compiledProgramHash, pencil.compiledProgramHash)
        assertNotEquals(gPen.compiledProgramHash, painting.compiledProgramHash)
        assertNotEquals(pencil.compiledProgramHash, painting.compiledProgramHash)
        assertNotEquals(gPen.semanticExecutionHash, pencil.semanticExecutionHash)
        assertNotEquals(gPen.semanticExecutionHash, painting.semanticExecutionHash)
        assertNotEquals(pencil.semanticExecutionHash, painting.semanticExecutionHash)
        assertEquals("CONTINUOUS_RIBBON", gPen.backend)
        assertEquals("PROCEDURAL_STAMP", pencil.backend)
        assertEquals("PROCEDURAL_STAMP", painting.backend)
        assertTrue(gPen.committedTileHash.isNotBlank())
        assertTrue(pencil.committedTileHash.isNotBlank())
        assertTrue(painting.committedTileHash.isNotBlank())

        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        val pixels = snapshot.copyPremultipliedPixels(
            app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight),
        )
        val output = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
            .resolve("default-brush-ui-raster.png")
        val bitmap = Bitmap.createBitmap(pixels, snapshot.logicalWidth, snapshot.logicalHeight, Bitmap.Config.ARGB_8888)
        try {
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        android.util.Log.i("InkloomBrushExecution", "UI raster artifact=${output.absolutePath}")
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cp ${output.absolutePath} /data/local/tmp/inkloom-default-brush-ui-raster.png",
            ),
        ).use { it.readBytes() }
        val ink = alphaProfile(snapshot, .28f)
        val graphite = alphaProfile(snapshot, .50f)
        val dryPaint = alphaProfile(snapshot, .72f)
        assertTrue("UI-selected G Pen and Pencil still look collapsed: ${ink.contentToString()} / ${graphite.contentToString()}", profileDistance(ink, graphite) > 14f)
        assertTrue("UI-selected G Pen and Painting Brush still look collapsed: ${ink.contentToString()} / ${dryPaint.contentToString()}", profileDistance(ink, dryPaint) > 10f)
        assertTrue(
            "UI-selected Pencil and Painting Brush still look collapsed at raster level",
            alignedAlphaMismatch(snapshot, .50f, .72f) > .25f,
        )

        composeRule.activityRule.scenario.recreate()
        val restoredView = waitForCanvas()
        composeRule.waitUntil(5_000) { restoredView.brushId == "system-dry-painting-brush" }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatchBehaviorPattern(restoredView, .84f)
        }
        restoredView.awaitV5StrokeWorkerIdleForTest()
        composeRule.waitUntil(5_000) {
            restoredView.lastBrushExecutionFingerprint?.presetId == "system-dry-painting-brush"
        }
        assertEquals("system-dry-painting-brush", restoredView.lastBrushExecutionFingerprint?.presetId)
    }

    private fun selectAndDraw(
        view: InkCanvasView,
        group: String,
        brush: String,
        expectedId: String,
        row: Float,
    ): BrushExecutionFingerprint {
        if (composeRule.onAllNodesWithContentDescription("Close brushes panel").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithContentDescription("Brushes").performClick()
        }
        composeRule.onNodeWithContentDescription("Brush category $group").performClick()
        composeRule.onNodeWithContentDescription("Brush preset $brush").performClick()
        composeRule.waitUntil(5_000) { view.brushId == expectedId }
        // Hold nominal size constant so only each selected program's material semantics differ.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.baseWidth = 42f
            dispatchBehaviorPattern(view, row)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        composeRule.waitUntil(5_000) { view.lastBrushExecutionFingerprint?.presetId == expectedId }
        return requireNotNull(view.lastBrushExecutionFingerprint)
    }

    private fun waitForCanvas(): InkCanvasView {
        composeRule.waitUntil(5_000) { findInkCanvasView() != null }
        return requireNotNull(findInkCanvasView())
    }

    private fun findInkCanvasView(): InkCanvasView? {
        var found: InkCanvasView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun walk(view: android.view.View) {
                if (view is InkCanvasView && view.isShown) found = view
                else if (view is ViewGroup) repeat(view.childCount) { walk(view.getChildAt(it)) }
            }
            walk(composeRule.activity.window.decorView)
        }
        return found
    }

    /**
     * Fixed black-box behavior row: tap, pressure ramp, tilt ramp, then two
     * overlapping passes. Every event still enters through View dispatch and
     * the production StrokeSession/Active Delta/PixelTileSurface path.
     */
    private fun dispatchBehaviorPattern(view: InkCanvasView, row: Float) {
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, elapsed: Long, pressure: Float, tilt: Float): MotionEvent =
            MotionEvent.obtain(
                downTime,
                downTime + elapsed,
                action,
                1,
                properties,
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    this.pressure = pressure
                    size = .02f
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
        // A short two-event stroke is used instead of DOWN/UP at one coordinate
        // so both ribbon and stamp backends expose a visible tip footprint.
        stroke(listOf(
            floatArrayOf(w * .08f, y, 0f, .65f, .10f),
            floatArrayOf(w * .085f, y, 4f, .65f, .10f),
        ))
        stroke(listOf(
            floatArrayOf(w * .14f, y, 10f, .12f, .12f),
            floatArrayOf(w * .24f, y - 10f, 18f, .35f, .12f),
            floatArrayOf(w * .34f, y + 8f, 26f, .68f, .12f),
            floatArrayOf(w * .44f, y, 34f, 1f, .12f),
        ))
        stroke(listOf(
            floatArrayOf(w * .49f, y, 40f, .72f, .02f),
            floatArrayOf(w * .58f, y - 10f, 48f, .72f, .28f),
            floatArrayOf(w * .67f, y + 8f, 56f, .72f, .55f),
            floatArrayOf(w * .76f, y, 64f, .72f, .82f),
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

    /** mean alpha, intermediate-alpha ratio, opaque ratio, occupied ratio */
    private fun alphaProfile(snapshot: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot, row: Float): FloatArray {
        val top = (snapshot.logicalHeight * (row - .055f)).toInt().coerceAtLeast(0)
        val bottom = (snapshot.logicalHeight * (row + .055f)).toInt().coerceAtMost(snapshot.logicalHeight)
        val left = (snapshot.logicalWidth * .16f).toInt()
        val right = (snapshot.logicalWidth * .80f).toInt()
        val pixels = snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(left, top, right, bottom))
        val alphas = pixels.map { it ushr 24 }
        return floatArrayOf(
            alphas.average().toFloat(),
            alphas.count { it in 8..239 }.toFloat() / alphas.size,
            alphas.count { it >= 240 }.toFloat() / alphas.size,
            alphas.count { it > 0 }.toFloat() / alphas.size,
        )
    }

    private fun profileDistance(a: FloatArray, b: FloatArray): Float =
        kotlin.math.abs(a[0] - b[0]) + 100f * (1 until a.size).sumOf { kotlin.math.abs(a[it] - b[it]).toDouble() }.toFloat()

    private fun alignedAlphaMismatch(
        snapshot: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot,
        firstRow: Float,
        secondRow: Float,
    ): Float {
        val halfHeight = (snapshot.logicalHeight * .055f).toInt()
        val left = (snapshot.logicalWidth * .16f).toInt()
        val right = (snapshot.logicalWidth * .80f).toInt()
        fun pixels(row: Float): IntArray {
            val center = (snapshot.logicalHeight * row).toInt()
            return snapshot.copyPremultipliedPixels(
                app.inkloom.engine.v5.surface.PixelRect(left, center - halfHeight, right, center + halfHeight),
            )
        }
        val first = pixels(firstRow)
        val second = pixels(secondRow)
        var relevant = 0
        var different = 0
        first.indices.forEach { index ->
            val a = first[index] ushr 24
            val b = second[index] ushr 24
            if (a > 0 || b > 0) {
                relevant++
                if (kotlin.math.abs(a - b) >= 12) different++
            }
        }
        return different.toFloat() / relevant.coerceAtLeast(1)
    }
}
