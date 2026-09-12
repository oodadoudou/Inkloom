package app.inkloom

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.color.MixingBoard
import app.inkloom.ui.ColorMixingView
import app.inkloom.ui.ColorMixingSession
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.UUID

class ColorMixingViewportTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun changingPaletteHostsRetainsViewportAndOldDetachCannotCancelNewContact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "mix-hosts-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var session: ColorMixingSession
                lateinit var old: ColorMixingView
                lateinit var next: ColorMixingView
                lateinit var host: FrameLayout
                scenario.onActivity { activity ->
                    session = ColorMixingSession(isolated)
                    host = FrameLayout(activity)
                    old = ColorMixingView(activity, session)
                    host.addView(old, FrameLayout.LayoutParams(600, 400))
                    activity.setContentView(host)
                }
                waitUntil { var ready = false; instrumentation.runOnMainSync { ready = old.ready && old.width > 0 }; ready }
                lateinit var saved: IntArray
                var scale = 0f
                var pan = 0f
                instrumentation.runOnMainSync {
                    old.ink = android.graphics.Color.RED
                    touch(old, MotionEvent.ACTION_DOWN, 300f, 200f)
                    touch(old, MotionEvent.ACTION_UP, 300f, 200f)
                    saved = session.board.snapshot()
                    wheel(old, 3f, 200f, 100f)
                    scale = old.zoom
                    pan = session.panX
                    next = ColorMixingView(host.context, session)
                    host.addView(next, FrameLayout.LayoutParams(900, 600))
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(scale, next.zoom)
                    assertEquals(pan, session.panX)
                    var picked = 0
                    next.tool = MixingBoard.Tool.PICK; next.onPick = { picked = it }
                    val paintedX = (200f + 100f * scale) * 1.5f
                    val paintedY = (100f + 100f * scale) * 1.5f
                    touch(next, MotionEvent.ACTION_DOWN, paintedX, paintedY)
                    touch(next, MotionEvent.ACTION_UP, paintedX, paintedY)
                    assertTrue("Resizing preserves zoom and the visible board location", android.graphics.Color.red(picked) > 240 && android.graphics.Color.green(picked) < 90)
                    // A new host replaces an unfinished old-host contact. The old host
                    // can still receive CANCEL/detach during the popup exit animation.
                    touch(old, MotionEvent.ACTION_DOWN, 300f, 100f)
                    next.tool = MixingBoard.Tool.PAINT; next.ink = android.graphics.Color.BLUE
                    touch(next, MotionEvent.ACTION_DOWN, 450f, 450f)
                    touch(next, MotionEvent.ACTION_MOVE, 520f, 450f)
                    val preview = session.board.snapshot()
                    assertFalse(saved.contentEquals(preview))
                    host.removeView(old)
                    touch(old, MotionEvent.ACTION_CANCEL, 300f, 100f)
                    touch(old, MotionEvent.ACTION_UP, 300f, 100f)
                    assertArrayEquals("Old host disposal must not erase the new host's in-flight pigment", preview, session.board.snapshot())
                    touch(next, MotionEvent.ACTION_UP, 520f, 450f)
                    next.undo()
                    assertArrayEquals("Only the new contact commits; the old preview was cancelled", saved, session.board.snapshot())
                    next.redo()
                    assertFalse(saved.contentEquals(session.board.snapshot()))
                }
                waitUntil {
                    runCatching {
                        DataInputStream(File(directory, "color-mixing-board.bin").inputStream().buffered()).use { input ->
                            input.readInt(); val width = input.readInt(); val height = input.readInt()
                            val pixels = IntArray(width * height) { input.readInt() }
                            var equal = false
                            instrumentation.runOnMainSync { equal = pixels.contentEquals(session.board.snapshot()) }
                            equal
                        }
                    }.getOrDefault(false)
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun touchingOutsideZoomedOutBoardDoesNotKeepParentInterceptionLocked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageDirectory = File(context.cacheDir, "mix-outside-${UUID.randomUUID()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = storageDirectory
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var view: ColorMixingView
                lateinit var host: InterceptTrackingHost
                scenario.onActivity { activity ->
                    host = InterceptTrackingHost(activity)
                    view = ColorMixingView(isolatedContext)
                    host.addView(view, FrameLayout.LayoutParams(600, 400))
                    activity.setContentView(host)
                }
                waitUntil {
                    var ready = false
                    instrumentation.runOnMainSync { ready = view.ready && view.isAttachedToWindow && view.width > 0 }
                    ready
                }
                instrumentation.runOnMainSync {
                    wheel(view, -12f)
                    assertTrue("The board must leave an outside margin", view.zoom < .5f)
                    val original = view.board.snapshot()
                    touch(view, MotionEvent.ACTION_DOWN, 10f, 10f)
                    assertFalse("A rejected outside contact cannot capture the parent's gesture", host.disallowed)
                    touch(view, MotionEvent.ACTION_MOVE, 30f, 20f)
                    touch(view, MotionEvent.ACTION_UP, 30f, 20f)
                    assertFalse("Rejected contact must release interception on UP", host.disallowed)
                    assertArrayEquals(original, view.board.snapshot())
                    assertFalse(view.board.canUndo)
                    touch(view, MotionEvent.ACTION_DOWN, 290f, 200f)
                    assertTrue("A valid next stroke still owns its gesture", host.disallowed)
                    touch(view, MotionEvent.ACTION_MOVE, 320f, 200f)
                    touch(view, MotionEvent.ACTION_UP, 320f, 200f)
                    assertFalse("The valid stroke also releases the parent", host.disallowed)
                    assertTrue("A valid next stroke commits normally", view.board.canUndo)
                    assertFalse(original.contentEquals(view.board.snapshot()))
                }
                waitUntil { File(storageDirectory, "color-mixing-board.bin").isFile }
            }
        } finally { storageDirectory.deleteRecursively() }
    }

    private class InterceptTrackingHost(context: Context) : FrameLayout(context) {
        var disallowed = false
            private set
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            disallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    @Test fun detachRollsBackUnfinishedPixelsAndReattachmentHasNoPreviewGhost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageDirectory = File(context.cacheDir, "mix-detach-${UUID.randomUUID()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = storageDirectory
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var view: ColorMixingView
                lateinit var host: FrameLayout
                scenario.onActivity { activity ->
                    host = FrameLayout(activity)
                    view = ColorMixingView(isolatedContext).apply { backgroundTone = android.graphics.Color.WHITE }
                    host.addView(view, FrameLayout.LayoutParams(600, 400))
                    activity.setContentView(host)
                }
                waitUntil {
                    var ready = false
                    instrumentation.runOnMainSync { ready = view.ready && view.isAttachedToWindow && view.width > 0 }
                    ready
                }
                lateinit var blank: IntArray
                instrumentation.runOnMainSync {
                    blank = renderedPixels(view)
                    touch(view, MotionEvent.ACTION_DOWN, 150f, 200f)
                    touch(view, MotionEvent.ACTION_MOVE, 400f, 200f)
                    touch(view, MotionEvent.ACTION_CANCEL, 400f, 200f)
                    assertArrayEquals("An ordinary cancel also clears the preview", blank, renderedPixels(view))
                    assertFalse(view.board.canUndo)
                    touch(view, MotionEvent.ACTION_DOWN, 150f, 200f)
                    touch(view, MotionEvent.ACTION_MOVE, 400f, 200f)
                    assertFalse("The unfinished preview must actually be visible before detach", blank.contentEquals(renderedPixels(view)))
                    assertTrue(view.board.snapshot().any { it != 0 })
                    // Deliver to the View directly so the parent has no touch target to
                    // cancel on removal. This exercises detach's own cancellation path.
                    host.removeView(view)
                    assertFalse(view.isAttachedToWindow)
                    assertTrue("The model rolls back the unfinished stroke", view.board.snapshot().all { it == 0 })
                    assertArrayEquals("The display cache rolls back with the model", blank, renderedPixels(view))
                    assertFalse("Cancelled preview is not an undo item", view.board.canUndo)
                    host.addView(view, FrameLayout.LayoutParams(600, 400))
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertTrue(view.isAttachedToWindow)
                    assertArrayEquals("Reopening the same remembered View cannot revive cancelled ink", blank, renderedPixels(view))
                    touch(view, MotionEvent.ACTION_DOWN, 150f, 200f)
                    touch(view, MotionEvent.ACTION_MOVE, 400f, 200f)
                    touch(view, MotionEvent.ACTION_UP, 400f, 200f)
                    assertTrue("New strokes still commit after reattachment", view.board.canUndo)
                    assertFalse(blank.contentEquals(renderedPixels(view)))
                    view.undo()
                    assertArrayEquals("Undo returns to the clean board", blank, renderedPixels(view))
                }
                // Let the coalesced final empty-board save finish before deleting only
                // this test's isolated storage directory.
                waitUntil {
                    runCatching {
                        DataInputStream(File(storageDirectory, "color-mixing-board.bin").inputStream().buffered()).use { stream ->
                            stream.readInt()
                            val width = stream.readInt(); val height = stream.readInt()
                            width == view.board.width && height == view.board.height && (0 until width * height).all { stream.readInt() == 0 }
                        }
                    }.getOrDefault(false)
                }
            }
        } finally {
            storageDirectory.deleteRecursively()
        }
    }

    private fun renderedPixels(view: ColorMixingView): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        } finally { bitmap.recycle() }
    }

    private fun touch(view: ColorMixingView, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() }
    }

    private fun wheel(view: ColorMixingView, amount: Float, pointX: Float = view.width / 2f, pointY: Float = view.height / 2f) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { x = pointX; y = pointY; setAxisValue(MotionEvent.AXIS_VSCROLL, amount) })
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
        try { assertTrue(view.dispatchGenericMotionEvent(event)) } finally { event.recycle() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(16)
        assertTrue("Mixing view did not finish attaching or saving", condition())
    }

    @Test fun backgroundIsPresentationOnlyAndNavigationDoesNotPaint() {
        instrumentation.runOnMainSync {
            val view = ColorMixingView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 600, 400)
            view.board.begin(100f, 100f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 10f)
            view.board.finish()
            val original = view.board.snapshot()
            view.backgroundTone = android.graphics.Color.WHITE
            val white = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(white))
            view.backgroundTone = android.graphics.Color.BLACK
            val black = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(black))
            assertEquals(android.graphics.Color.WHITE, white.getPixel(0, 0))
            assertEquals(android.graphics.Color.BLACK, black.getPixel(0, 0))
            assertArrayEquals(original, view.board.snapshot())
            assertTrue(view.board.canUndo)
            white.recycle(); black.recycle()
        }
    }
    @Test fun wheelZoomKeepsPixelsAndResetRestoresViewport() {
        instrumentation.runOnMainSync {
            val view = ColorMixingView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 600, 400)
            val original = view.board.snapshot()
            wheel(view, 3f)
            assertTrue(view.zoom > 1f)
            assertArrayEquals(original, view.board.snapshot())
            assertFalse(view.board.canUndo)
            view.resetViewport(); assertEquals(1f, view.zoom)
        }
    }
}
