package app.inkloom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import android.view.ViewGroup
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.ViewportState
import app.inkloom.document.ViewportTransform
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.InkViewportTouchProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression: after a two-finger pinch that zooms the viewport (rendered via
 * `Modifier.graphicsLayer(scaleX=zoom, ...)` on the PaperCanvas), touches no longer
 * reach the InkCanvasView AndroidView, so the next stroke never commits.
 */
@RunWith(AndroidJUnit4::class)
class InkloomPinchThenWriteTest {
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var activeDocumentId: String? = null

    private val prepareRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                context.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .putString("language", "ENGLISH")
                    .putBoolean("fingerDrawing", true)
                    .putBoolean("singleFingerDoubleTapUndo", false)
                    .putBoolean("twoFingerDoubleTapUndo", false)
                    .putBoolean("threeFingerTapRedo", false)
                    .putBoolean("twoFingerRotation", false)
                    .putBoolean("rulerEnabled", false)
                    .putBoolean("gridSnapEnabled", false)
                    .putBoolean("alignmentGuidesEnabled", false)
                    .putBoolean("showInputDiagnostics", false)
                    .putBoolean("showPageRail", true)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showToolbar", true)
                    .putString("pagePanelDock", "LEFT")
                    .putString("brushPanelDock", "RIGHT")
                    .putString("layerPanelDock", "RIGHT")
                    .putFloat("pagePanelXDp", 16f)
                    .putFloat("pagePanelYDp", 16f)
                    .putFloat("pagePanelWidthDp", 160f)
                    .putFloat("pagePanelHeightDp", 640f)
                    .putFloat("brushPanelXDp", 720f)
                    .putFloat("brushPanelYDp", 16f)
                    .putFloat("brushPanelWidthDp", 330f)
                    .putFloat("brushPanelHeightDp", 680f)
                    .putFloat("layerPanelXDp", 720f)
                    .putFloat("layerPanelYDp", 16f)
                    .putFloat("layerPanelWidthDp", 300f)
                    .putFloat("layerPanelHeightDp", 620f)
                    .commit()
                val documentsDir = File(context.filesDir, "documents")
                documentsDir.listFiles()?.forEach { it.delete() }
                File(context.filesDir, "journal.jsonl").delete()
                activeDocumentId = null
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(prepareRule).around(composeRule)

    private fun openEditor() {
        // Return to the library if a previous test left the editor open.
        runCatching { composeRule.onNodeWithText("Gallery").performClick() }
        composeRule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = FileDocumentStore(context.filesDir)
        val documentIdsBeforeCreate = store.list().mapTo(mutableSetOf()) { it.id }
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create notebook").assertIsDisplayed()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { findInkCanvasView() != null }
        composeRule.waitUntil(10_000) { store.list().any { it.id !in documentIdsBeforeCreate } }
        activeDocumentId = store.list().first { it.id !in documentIdsBeforeCreate }.id
    }

    private fun strokeCount(): Int {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = FileDocumentStore(context.filesDir)
        val doc = activeDocumentId?.let(store::get)
        return doc?.pages?.sumOf { it.strokes.size } ?: 0
    }

    @Test fun canWriteAfterNativeTwoFingerPinch() {
        openEditor()
        composeRule.waitUntil(5_000) { strokeCount() == 0 }

        // Baseline: a single finger must be able to draw.
        composeRule.onRoot().performTouchInput {
            down(0, center + Offset(-80f, 80f))
            moveTo(0, center + Offset(80f, 80f), delayMillis = 20)
            up(0)
        }
        composeRule.waitUntil(10_000) { strokeCount() == 1 }
        assertEquals("A single finger must draw before any pinch", 1, strokeCount())

        // Two-finger pinch on the paper (zooms the viewport past ~2x).
        composeRule.onRoot().performTouchInput {
            down(0, center + Offset(-160f, 0f))
            down(1, center + Offset(160f, 0f))
            moveTo(0, center + Offset(-320f, 0f), delayMillis = 30)
            moveTo(1, center + Offset(320f, 0f), delayMillis = 30)
            moveTo(0, center + Offset(-380f, 20f), delayMillis = 30)
            moveTo(1, center + Offset(380f, 20f), delayMillis = 30)
            up(1)
            up(0)
        }
        composeRule.waitForIdle()
        assertEquals("A pinch must never leave ink", 1, strokeCount())

        // A single finger must be able to draw again right after the pinch.
        composeRule.onRoot().performTouchInput {
            down(0, center + Offset(-100f, -60f))
            moveTo(0, center + Offset(100f, -60f), delayMillis = 20)
            up(0)
        }
        composeRule.waitUntil(10_000) { strokeCount() == 2 }
        assertEquals("Finger must draw immediately after a native pinch", 2, strokeCount())
    }

    @Test fun strokeRendersAtZoom1() {
        openEditor()
        composeRule.waitUntil(5_000) { strokeCount() == 0 }

        composeRule.onRoot().performTouchInput {
            down(0, center + Offset(0f, 250f))
            moveTo(0, center + Offset(0f, 320f), delayMillis = 30)
            up(0)
        }
        composeRule.waitUntil(10_000) { strokeCount() == 1 }
        composeRule.waitForIdle()
        android.os.SystemClock.sleep(500)

        val view = findInkCanvasView()!!
        var white = 0; var dark = 0; var transparent = 0
        val ascii = StringBuilder("\nVIEWBITMAP")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            for (ry in 0 until 24) {
                ascii.append("\n")
                for (rx in 0 until 40) {
                    val x = view.width * rx / 40
                    val y = view.height * ry / 24
                    val px = bmp.getPixel(x, y)
                    val a = android.graphics.Color.alpha(px)
                    val lum = android.graphics.Color.red(px) + android.graphics.Color.green(px) + android.graphics.Color.blue(px)
                    if (a == 0) { transparent++; ascii.append(" ") }
                    else if (lum > 700) { white++; ascii.append(".") }
                    else if (lum < 600) { dark++; ascii.append("#") }
                    else { ascii.append("-") }
                }
            }
            bmp.recycle()
        }
        System.err.println("VIEWBITMAP white=$white dark=$dark transparent=$transparent")
        System.err.println(ascii.toString())
        assertTrue("The committed stroke must render on the canvas (dark=$dark)", dark > 0)
    }

    @Test fun zoomedPageRendersBeyondLayoutBounds() {
        openEditor()
        composeRule.waitUntil(5_000) { strokeCount() == 0 }

        val view = findInkCanvasView()
        val location = IntArray(2)
        view!!.getLocationOnScreen(location)
        val paperCentreX = location[0] + view.width / 2f
        val paperCentreY = location[1] + view.height / 2f
        // Keep the entire initial contact inside the current paper even when system insets or
        // compact toolbars reduce the editor height. The exercised pinch moves this band beyond
        // the original AndroidView bounds, which is the clipping regression this test protects.
        val startDy = view.height * .24f
        val endDy = view.height * .30f
        var setupStrokes = emptyList<app.inkloom.document.InkStroke>()

        // The native pinch/write test covers proxy routing. Here a local stylus stream creates a
        // deterministic stroke whose transformed rendering is then checked outside layout bounds.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onStrokesReplaced = { _, replacements, _ -> setupStrokes = replacements }
            val downTime = android.os.SystemClock.uptimeMillis()
            fun dispatch(action: Int, y: Float, elapsed: Long) {
                val event = android.view.MotionEvent.obtain(
                    downTime,
                    downTime + elapsed,
                    action,
                    1,
                    arrayOf(android.view.MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS
                    }),
                    arrayOf(android.view.MotionEvent.PointerCoords().apply {
                        x = view.width / 2f
                        this.y = view.height / 2f + y
                        pressure = .7f
                        size = .05f
                    }),
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0,
                )
                view.onTouchEvent(event)
                event.recycle()
            }
            dispatch(android.view.MotionEvent.ACTION_DOWN, startDy, 0)
            dispatch(android.view.MotionEvent.ACTION_MOVE, endDy, 20)
            dispatch(android.view.MotionEvent.ACTION_UP, endDy, 21)
            assertEquals("The local main canvas must commit the setup stroke", 1, (reflectField(view, "strokes") as? List<*>)?.size)
        }
        assertEquals("The setup stroke must leave the input engine", 1, setupStrokes.size)

        fun screenshot(): android.graphics.Bitmap {
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p")
            val rawPng = java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            return BitmapFactory.decodeByteArray(rawPng, 0, rawPng.size)
        }
        fun darkInColumn(bitmap: android.graphics.Bitmap, x: Int, yFrom: Int, yTo: Int): Int {
            var d = 0
            for (y in yFrom until yTo) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) < 600) d++
            }
            return d
        }

        // Before zoom, the stroke must be visible at its zoom-1 position (centre x, y 1080..1220).
        composeRule.waitForIdle()

        android.os.SystemClock.sleep(500)
        var viewDark = 0
        var strokesAtDraw = -1
        var tilesAtDraw = -1
        var dirtyAtDraw = -1
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            strokesAtDraw = (reflectField(view, "strokes") as? List<*>)?.size ?: -1
            val stroke0 = (reflectField(view, "strokes") as? List<*>)?.firstOrNull()
            val strokeWidth = stroke0?.let { reflectField(it, "width") as? Float }
            val strokePoints = stroke0?.let { (reflectField(it, "points") as? List<*>)?.size } ?: -1
            val firstPoint = stroke0?.let { (reflectField(it, "points") as? List<*>)?.firstOrNull() }
            val firstX = firstPoint?.let { reflectField(it, "x") as? Float }
            val firstY = firstPoint?.let { reflectField(it, "y") as? Float }
            System.err.println("RENDER stroke width=$strokeWidth points=$strokePoints first=($firstX,$firstY)")
            val committedScene = view.engineV2CommittedTileSnapshot()
            tilesAtDraw = committedScene.tileCount
            dirtyAtDraw = committedScene.dirtyTileCount
            var tileDark = 0
            committedScene.tiles.forEach { bmp ->
                for (y in 0 until bmp.height) for (x in 0 until bmp.width step 2) {
                    val pixel = bmp.getPixel(x, y)
                    if (android.graphics.Color.alpha(pixel) > 0 && android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) < 600) tileDark++
                }
            }
            System.err.println("RENDER beforeZoom tileDark=$tileDark tiles=${committedScene.tileCount}")
            val firstTile = committedScene.tiles.firstOrNull()
            if (firstTile != null) {
                val sb = StringBuilder("TILE0 (${firstTile.width}x${firstTile.height})")
                for (ry in 0 until 16) {
                    val y0 = firstTile.height * ry / 16
                    sb.append("\n")
                    for (rx in 0 until 32) {
                        val x0 = firstTile.width * rx / 32
                        val px = firstTile.getPixel(x0, y0)
                        val lum = android.graphics.Color.red(px) + android.graphics.Color.green(px) + android.graphics.Color.blue(px)
                        sb.append(if (lum < 600) "#" else if (android.graphics.Color.alpha(px) == 0) " " else ".")
                    }
                }
                System.err.println(sb.toString())
            }
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            view.draw(c)
            var white = 0; var transparent = 0; var dMinX = Int.MAX_VALUE; var dMinY = Int.MAX_VALUE; var dMaxX = -1; var dMaxY = -1
            for (y in 0 until view.height) for (x in 0 until view.width step 2) {
                val pixel = bmp.getPixel(x, y)
                val a = android.graphics.Color.alpha(pixel)
                val lum = android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
                if (a == 0) transparent++
                else if (lum > 700) white++
                if (a > 0 && lum < 600) { if (x < dMinX) dMinX = x; if (y < dMinY) dMinY = y; if (x > dMaxX) dMaxX = x; if (y > dMaxY) dMaxY = y; viewDark++ }
            }
            System.err.println("RENDER viewDraw white=$white transparent=$transparent dark=$viewDark darkBbox=($dMinX,$dMinY)-($dMaxX,$dMaxY)")
            bmp.recycle()
        }
        System.err.println("RENDER beforeZoom strokes=$strokesAtDraw tiles=$tilesAtDraw dirty=$dirtyAtDraw viewOnDrawDark=$viewDark")
        val before = screenshot()
        val beforeDark = darkInColumn(before, (paperCentreX * (before.width / 2560f)).toInt(), (1080 * (before.height / 1600f)).toInt(), (1220 * (before.height / 1600f)).toInt())
        System.err.println("RENDER beforeZoom darkInStrokeBand=$beforeDark")
        val beforeCenterDark = darkInColumn(before, (paperCentreX * (before.width / 2560f)).toInt(), (600 * (before.height / 1600f)).toInt(), (1300 * (before.height / 1600f)).toInt())
        System.err.println("RENDER beforeZoom darkInCenterColumn=$beforeCenterDark")

        composeRule.onRoot().performTouchInput {
            down(0, center + Offset(-160f, 0f))
            down(1, center + Offset(160f, 0f))
            moveTo(0, center + Offset(-320f, 0f), delayMillis = 30)
            moveTo(1, center + Offset(320f, 0f), delayMillis = 30)
            moveTo(0, center + Offset(-380f, 0f), delayMillis = 30)
            moveTo(1, center + Offset(380f, 0f), delayMillis = 30)
            up(1)
            up(0)
        }
        composeRule.waitForIdle()
        // Viewport recomposition reloads the document scene. Reinstall this local fixture so this
        // test remains focused on transformed overflow rendering; persistence is covered above.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.setDocumentStrokes(setupStrokes, "overflow-render-fixture")
        }

        val scale = reflectField(view, "viewportScale") as Float
        val panX = reflectField(view, "viewportPanX") as Float
        val panY = reflectField(view, "viewportPanY") as Float
        val canvasStrokeCount = (reflectField(view, "strokes") as? List<*>)?.size
        val diskCount = strokeCount()
        System.err.println("RENDER STATE scale=$scale pan=($panX,$panY) canvasStrokes=$canvasStrokeCount diskStrokes=$diskCount")
        assertTrue("Pinch must zoom the viewport (scale=$scale)", scale > 1.5f)

        // The whole PaperCanvas is physically transformed, so both its white paper and its ink
        // use screenY = paperCentreY + panY + scale * dy for dy in [startDy, endDy].
        val startScreenY = paperCentreY + panY + scale * startDy
        val endScreenY = paperCentreY + panY + scale * endDy
        val canvasBottom = location[1] + view.height
        assertTrue("Stroke must map below the canvas layout bottom ($canvasBottom) after zoom ($startScreenY..$endScreenY)", startScreenY > canvasBottom)

        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p")
        val rawPng = java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(rawPng, 0, rawPng.size)
        val scaleX = bitmap.width / 2560f
        val scaleY = bitmap.height / 1600f
        val probeX = ((paperCentreX + panX) * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        var dark = 0
        for (y in startScreenY.toInt() until endScreenY.toInt() step 2) {
            val probeY = (y * scaleY).toInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(probeX, probeY)
            val luminance = android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
            if (luminance < 600) dark++
        }
        // Bounding box of every dark pixel (excluding the top toolbar band above y=400).
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1; var count = 0
        for (y in 400 until bitmap.height) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) < 600) {
                    count++
                    if (x < minX) minX = x; if (y < minY) minY = y; if (x > maxX) maxX = x; if (y > maxY) maxY = y
                }
            }
        }
        System.err.println("RENDER scale=$scale pan=($panX,$panY) startScreenY=$startScreenY endScreenY=$endScreenY probeX=$probeX dark=$dark allDarkCount=$count bbox=(${minX / scaleX},${minY / scaleY})-(${maxX / scaleX},${maxY / scaleY})")
        val cols = 48
        val rows = 20
        val sb = StringBuilder("\nSCREEN")
        for (ry in 0 until rows) {
            val y0 = 400 + (bitmap.height - 400) * ry / rows
            val y1 = 400 + (bitmap.height - 400) * (ry + 1) / rows
            sb.append("\n")
            for (rx in 0 until cols) {
                val x0 = bitmap.width * rx / cols
                val x1 = bitmap.width * (rx + 1) / cols
                var darkCount = 0; var total = 0
                for (y in y0 until y1 step 3) for (x in x0 until x1 step 3) {
                    total++
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) < 420) darkCount++
                }
                sb.append(if (darkCount > total / 3) "#" else if (darkCount > 0) "." else " ")
            }
        }
        System.err.println(sb.toString())
        assertTrue("Zoomed ink must be visible on screen below the canvas's layout bounds (dark=$dark)", dark >= 3)

        // The same visible overflow must also remain writable. Android hit testing normally stops
        // at the untransformed View bounds, so this contact exercises the full-viewport proxy.
        val overflowX = paperCentreX + panX
        val overflowY = canvasBottom + 28f
        assertTrue("The regression contact must be below the original page View", overflowY > canvasBottom)
        assertTrue("The regression contact must remain on the physical display", overflowY < bitmap.height / scaleY)
        val expectedPagePoint = ViewportTransform.viewToPage(
            overflowX - location[0],
            overflowY - location[1],
            view.width.toFloat(),
            view.height.toFloat(),
            ViewportState(scale, panX, panY),
        )
        sendScreenFingerStroke(overflowX, overflowY, overflowX + 28f, overflowY + 12f)
        composeRule.waitUntil(10_000) { strokeCount() == 1 }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val overflowStroke = activeDocumentId?.let { FileDocumentStore(context.filesDir).get(it) }
            ?.pages?.flatMap { it.strokes }?.last()
        assertEquals("Overflow writing must inverse-map to the physical contact X", expectedPagePoint.first / view.width, overflowStroke?.points?.first()?.x ?: Float.NaN, .004f)
        assertEquals("Overflow writing must inverse-map to the physical contact Y", expectedPagePoint.second / view.height, overflowStroke?.points?.first()?.y ?: Float.NaN, .004f)
    }

    private fun sendScreenFingerStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float, elapsed: Long) {
            val event = android.view.MotionEvent.obtain(downTime, downTime + elapsed, action, x, y, 0).apply {
                source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(android.view.MotionEvent.ACTION_DOWN, startX, startY, 0)
        send(android.view.MotionEvent.ACTION_MOVE, endX, endY, 20)
        send(android.view.MotionEvent.ACTION_UP, endX, endY, 40)
    }

    private fun findInkCanvasView(): InkCanvasView? {
        var found: InkCanvasView? = null
        var foundArea = -1L
        var proxyTarget: InkCanvasView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun walk(view: android.view.View) {
                if (view is InkViewportTouchProxy && view.isShown) {
                    view.target?.takeIf { it.isShown && it.isEnabled }?.let { proxyTarget = it }
                } else if (view is InkCanvasView && view.isShown && view.isEnabled) {
                    val area = view.width.toLong() * view.height.toLong()
                    if (area > foundArea) {
                        found = view
                        foundArea = area
                    }
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
            }
            walk(composeRule.activity.window.decorView)
        }
        return proxyTarget ?: found
    }

    private fun reflectField(target: Any, name: String): Any? {
        var field: java.lang.reflect.Field? = null
        var type: Class<*>? = target.javaClass
        while (type != null && field == null) {
            field = runCatching { type.getDeclaredField(name) }.getOrNull()
            type = type.superclass
        }
        field?.isAccessible = true
        return field?.get(target)
    }
}
