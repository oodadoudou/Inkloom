package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v5.tools.RasterAbyssPolicy
import app.inkloom.engine.v5.tools.RasterDependency
import app.inkloom.engine.v5.tools.RasterExecutionMode
import app.inkloom.engine.v5.tools.RasterRoiPlan
import app.inkloom.engine.v5.tools.RasterTileEditSession
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterTileEditSessionTest {
    @Test fun transparentAbyssKeepsTheRequestedReadCoordinateSystem() {
        val display = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val surface = PixelTileBitmapAdapter.import(display)
        val plan = RasterRoiPlan.create(
            PixelRect(0, 0, 1, 1),
            surface.logicalWidth,
            surface.logicalHeight,
            surface.tileSize,
            readCore = PixelRect(-2, -1, 2, 2),
            abyssPolicy = RasterAbyssPolicy.TRANSPARENT,
        )

        RasterTileEditSession(surface, "transparent abyss").use { session ->
            session.editWithRoi(plan) { sample, global, _ ->
                assertEquals(PixelRect(-2, -1, 2, 2), global)
                assertEquals(4, sample.width)
                assertEquals(3, sample.height)
                assertEquals(Color.TRANSPARENT, sample.getPixel(0, 0))
                assertEquals(Color.RED, sample.getPixel(2, 1))
                assertEquals(Color.RED, sample.getPixel(3, 2))
            }
        }
        display.recycle()
    }

    @Test fun clampAbyssRepeatsEdgesWhilePreservingTheOverlappingAxis() {
        val display = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) for (x in 0 until 4) display.setPixel(x, y, Color.rgb(x * 40, y * 50, 10))
        val surface = PixelTileBitmapAdapter.import(display)
        val plan = RasterRoiPlan.create(
            PixelRect(0, 0, 1, 1),
            surface.logicalWidth,
            surface.logicalHeight,
            surface.tileSize,
            readCore = PixelRect(1, 5, 3, 7),
            abyssPolicy = RasterAbyssPolicy.CLAMP_TO_EDGE,
        )

        RasterTileEditSession(surface, "clamped abyss").use { session ->
            session.editWithRoi(plan) { sample, global, _ ->
                assertEquals(PixelRect(1, 5, 3, 7), global)
                assertEquals(display.getPixel(1, 3), sample.getPixel(0, 0))
                assertEquals(display.getPixel(2, 3), sample.getPixel(1, 0))
                assertEquals(display.getPixel(1, 3), sample.getPixel(0, 1))
                assertEquals(display.getPixel(2, 3), sample.getPixel(1, 1))
            }
        }
        display.recycle()
    }

    @Test fun independentKernelUsesTheBoundedWorkerPoolAndKeepsOneUndoRecord() {
        val display = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val surface = PixelTileBitmapAdapter.import(display)
        val callbackThreads = Collections.synchronizedSet(mutableSetOf<String>())
        val session = RasterTileEditSession(surface, "independent kernel")

        session.editIndependent(PixelRect(0, 0, 512, 512)) { bitmap, _ ->
            callbackThreads += Thread.currentThread().name
            bitmap.eraseColor(Color.BLUE)
        }
        assertEquals(RasterDependency.INDEPENDENT, session.lastRoiPlan?.dependency)
        assertEquals(RasterExecutionMode.PARALLEL, session.lastRoiPlan?.executionMode)
        assertTrue(callbackThreads.isNotEmpty())
        assertTrue(callbackThreads.all { it == "Inkloom-RasterROI" })
        assertEquals(Color.BLUE, session.previewSnapshot().pixelAt(300, 300))

        val record = session.commit()
        assertEquals(16, record.changedTileCount)
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(Color.RED, surface.pixelAt(300, 300))
        record.close()
        session.close()
        display.recycle()
    }

    @Test fun noOpScratchRoundTripDoesNotDriftPremultipliedPixels() {
        val display = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        for (y in 0 until display.height) for (x in 0 until display.width) {
            display.setPixel(x, y, Color.argb((17 + x + y) % 255, (x * 7) % 255, (y * 11) % 255, (x + y * 3) % 255))
        }
        val surface = PixelTileBitmapAdapter.import(display)
        val before = surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 192, 192))
        val record = RasterTileEditSession(surface, "no-op round trip").use { session ->
            session.edit(PixelRect(23, 29, 171, 177)) { }
            session.commit()
        }
        val after = surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 192, 192))
        assertTrue("Premultiplied pixels must survive an Android scratch round trip", before.contentEquals(after))
        assertEquals(0, record.changedTileCount)
        record.close()
        display.recycle()
    }

    @Test fun editCommitsOnlyTouchedTilesAndProducesAnExactUndoRecord() {
        val display = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val surface = PixelTileBitmapAdapter.import(display)
        val bounds = PixelRect(110, 120, 190, 210)
        val session = RasterTileEditSession(surface, "tile paint")

        session.edit(bounds) { scratch ->
            Canvas(scratch).drawColor(Color.BLUE)
        }
        assertEquals("Private sparse preview updates before commit", Color.BLUE, session.previewSnapshot().pixelAt(150, 160))
        assertEquals("Published tile surface stays atomic", Color.RED, surface.pixelAt(150, 160))
        assertTrue("Only intersecting tiles are copied", session.touchedTileCount <= 4)

        val record = session.commit()
        assertEquals(Color.BLUE, surface.pixelAt(150, 160))
        assertEquals(Color.RED, surface.pixelAt(40, 40))
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(Color.RED, surface.pixelAt(150, 160))
        record.close()
        display.recycle()
    }

    @Test fun cancelDropsThePrivatePreviewAndLeavesTheSurfaceUnpublished() {
        val display = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val surface = PixelTileBitmapAdapter.import(display)
        RasterTileEditSession(surface, "cancelled tile edit").use { session ->
            session.edit(PixelRect(20, 30, 100, 110)) { scratch ->
                Canvas(scratch).drawRect(0f, 0f, scratch.width.toFloat(), scratch.height.toFloat(), Paint().apply { color = Color.MAGENTA })
            }
            assertEquals(Color.MAGENTA, session.previewSnapshot().pixelAt(50, 50))
        }

        assertEquals(Color.GREEN, surface.pixelAt(50, 50))
        display.recycle()
    }
}
