package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.FillSubTool
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.settings.ReferenceTarget
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillToolTest {
    @Test fun referenceSelectionIncludesVisibleAdjustmentsAndKeepsExcludedScope() {
        val ink = app.inkloom.document.InkLayer(id="ink",name="Ink")
        val adjustment = app.inkloom.document.InkLayer(id="adjust",name="Invert",role=app.inkloom.document.LayerRole.ADJUSTMENT,
            adjustment=app.inkloom.document.LayerAdjustment(app.inkloom.document.LayerAdjustmentKind.INVERT))
        val view = canvas {
            setDocumentLayers(listOf(ink,adjustment)); layerOrder=listOf("ink","adjust")
            setRasterLayerSurfaces(mapOf("ink" to borderSurface()))
            pageModelWidth=200f;pageModelHeight=200f;pagePaperColor=Color.WHITE;activeLayerId="ink"
            bindDocumentSelectionState(app.inkloom.engine.v5.selection.DocumentSelectionRuntime(64).stateFor("reference-adjustments","page",200,200))
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse("Visible inversion turns the black border white for reference selection",view.selectColorRange(Color.BLACK,.05f,false))
            view.referenceTarget=ReferenceTarget.ACTIVE_LAYER
            assertTrue("Current layer deliberately excludes the adjustment",view.selectColorRange(Color.BLACK,.05f,false))
        }
    }

    @Test fun manualFillSubToolsCommitRasterMasks() {
        FillSubTool.entries.filter { it.drawsLasso && !it.referencesLines && !it.erases }.forEach { subTool ->
            var committed = false
            val view = canvas {
                tool = EditorTool.FILL
                fillSubTool = subTool
                fingerDrawingEnabled = true
                pagePaperColor = Color.WHITE
                onSparseFillCommitted = { mask, _, _, _ ->
                    committed = !mask.isEmpty && mask.nonZeroBounds() != null
                }
            }
            send(view, 0L, MotionEvent.ACTION_DOWN, 40f, 40f)
            send(view, 20L, MotionEvent.ACTION_MOVE, 160f, 40f)
            send(view, 40L, MotionEvent.ACTION_MOVE, 160f, 160f)
            send(view, 60L, MotionEvent.ACTION_MOVE, 40f, 160f)
            send(view, 80L, MotionEvent.ACTION_UP, 40f, 40f)
            assertTrue("$subTool must commit a reusable fill mask", committed)
        }
    }

    /** A lasso around the whole 200 px page. */
    private fun lassoAround(view: InkCanvasView, start: Long) {
        send(view, start, MotionEvent.ACTION_DOWN, 20f, 20f)
        send(view, start + 20L, MotionEvent.ACTION_MOVE, 180f, 20f)
        send(view, start + 40L, MotionEvent.ACTION_MOVE, 180f, 180f)
        send(view, start + 60L, MotionEvent.ACTION_MOVE, 20f, 180f)
        send(view, start + 80L, MotionEvent.ACTION_UP, 20f, 20f)
    }

    private fun linedCanvas(subTool: FillSubTool, block: InkCanvasView.() -> Unit = {}) = canvas {
        tool = EditorTool.FILL; fillSubTool = subTool; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
        pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = "color"
        referenceTarget = ReferenceTarget.ALL_VISIBLE
        layerOrder = listOf("color", "line")
        setRasterLayerSurfaces(mapOf("color" to PixelTileSurface(200, 200), "line" to borderSurface()))
        block()
    }

    @Test fun encloseAndFillFillsOnlyTheClosedRegionAndSpreadsUnderItsLines() {
        var mask: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
        val view = linedCanvas(FillSubTool.ENCLOSE_AND_FILL) { onSparseFillCommitted = { committed, _, _, _ -> mask = committed } }
        lassoAround(view, 0L)
        val result = checkNotNull(mask) { "enclose and fill must commit the closed region" }
        assertTrue("the inside of the square is filled", result.alphaAt(100, 100) > 0)
        assertTrue("the fill reaches under the inner edge of the line", result.alphaAt(54, 100) > 0)
        assertEquals("the region outside the square touches the lasso, so it stays empty", 0, result.alphaAt(30, 30))
        val bounds = checkNotNull(result.nonZeroBounds())
        assertTrue("the fill never crosses the line: $bounds", bounds.left >= 46 && bounds.right <= 155)
    }

    @Test fun encloseAndEraseClearsTheClosedRegionOfTheColourLayer() {
        val color = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("flat").use { w -> for (y in 0 until 200) for (x in 0 until 200) w.setPixel(x, y, Color.RED); w.commit().close() }
        }
        var erased: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
        var filled = false
        val view = linedCanvas(FillSubTool.ENCLOSE_AND_ERASE) {
            setRasterLayerSurfaces(mapOf("color" to color, "line" to borderSurface()))
            onSparseFillCommitted = { _, _, _, _ -> filled = true }
            onSparseEraseCommitted = { erased = it }
        }
        lassoAround(view, 0L)
        assertFalse("erasing never paints", filled)
        val result = checkNotNull(erased) { "the colour layer itself must not count as a line" }
        assertTrue(result.alphaAt(100, 100) > 0)
        assertEquals(0, result.alphaAt(30, 30))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { assertTrue(view.eraseMaskFromActiveRaster(result)) }
        assertEquals("the closed region is cleared", 0, Color.alpha(color.pixelAt(100, 100)))
        assertEquals("outside the line is kept", 255, Color.alpha(color.pixelAt(30, 30)))
    }

    @Test fun lassoEraseErasesTheWholeLasso() {
        var erased: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
        val view = linedCanvas(FillSubTool.LASSO_ERASE) { onSparseEraseCommitted = { erased = it } }
        lassoAround(view, 0L)
        val result = checkNotNull(erased)
        assertTrue(result.alphaAt(30, 30) > 0)
        assertTrue(result.alphaAt(100, 100) > 0)
    }

    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 200, 200); block() }
        }
        return result
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float, toolType: Int = MotionEvent.TOOL_TYPE_FINGER) {
        val props = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }

    @Test fun tappingWithTheFillToolCommitsAFillElement() {
        var committed = false
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = android.graphics.Color.WHITE
            onSparseFillCommitted = { mask, _, _, _ -> committed = !mask.isEmpty }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertTrue("A fill must be committed on tap", committed)
    }

    @Test fun draggingAdjustsSensitivityAndCommitsOneFill() {
        var commitCount = 0
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = android.graphics.Color.WHITE
            onSparseFillCommitted = { _, _, _, _ -> commitCount++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_MOVE, 160f, 100f)
        send(view, 100L, MotionEvent.ACTION_UP, 160f, 100f)
        assertEquals("Dragging then lifting commits exactly one fill", 1, commitCount)
    }

    @Test fun fingerCannotFillWhenFingerDrawingIsDisabled() {
        var commitCount = 0
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = false
            pagePaperColor = Color.WHITE
            onSparseFillCommitted = { _, _, _, _ -> commitCount++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(0, commitCount)
    }

    @Test fun fillPreviewUsesCanvasCoordinates() {
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            inkColor = Color.BLACK
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        val output = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(output)) }
        assertEquals("The preview should cover the touched canvas region", Color.BLACK, output.getPixel(100, 100))
        send(view, 50L, MotionEvent.ACTION_CANCEL, 100f, 100f)
        output.recycle()
    }

    @Test fun referenceAndDraftFoldersControlFillWithoutHidingSketches() {
        var committedWidth = 0
        val folder = app.inkloom.document.InkLayer(id = "reference-folder", name = "Sketch", role = app.inkloom.document.LayerRole.FOLDER,
            reference = true, draft = true)
        val line = app.inkloom.document.InkLayer(id = "line", name = "Line", parentId = folder.id)
        val paint = app.inkloom.document.InkLayer(id = "color", name = "Color")
        val pixels = PixelTileSurface(200, 200)
        pixels.beginWrite("border").use { w ->
            for (offset in -4..4) for (c in 50..150) {
                w.setPixel(c, 50 + offset, Color.BLACK); w.setPixel(c, 150 + offset, Color.BLACK)
                w.setPixel(50 + offset, c, Color.BLACK); w.setPixel(150 + offset, c, Color.BLACK)
            }
            w.commit().close()
        }
        val view = canvas {
            tool = EditorTool.FILL; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
            pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = paint.id
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS
            setDocumentLayers(listOf(paint, folder, line)); layerOrder = listOf(paint.id, line.id)
            setRasterLayerSurfaces(mapOf(paint.id to PixelTileSurface(200, 200), line.id to pixels))
            onSparseFillCommitted = { mask, _, _, _ -> committedWidth = mask.nonZeroBounds()?.width ?: 0 }
        }
        assertTrue(line.id in view.referenceLayerIds); assertTrue(line.id in view.draftLayerIds)
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f); send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(200, committedWidth)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeDraftLayersFromReference = false }
        send(view, 100L, MotionEvent.ACTION_DOWN, 100f, 100f); send(view, 150L, MotionEvent.ACTION_UP, 100f, 100f)
        assertTrue("Including a draft reference folder must restore its boundary", committedWidth in 80..120)
    }

    @Test fun lockedAndEditingLayerExclusionsChangeTheMultipleLayerFillReference() {
        var committedWidth = 0
        fun border() = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("border").use { w ->
                for (offset in -4..4) for (c in 50..150) {
                    w.setPixel(c, 50 + offset, Color.BLACK); w.setPixel(c, 150 + offset, Color.BLACK)
                    w.setPixel(50 + offset, c, Color.BLACK); w.setPixel(150 + offset, c, Color.BLACK)
                }
                w.commit().close()
            }
        }
        val view = canvas {
            tool = EditorTool.FILL; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
            pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = "color"
            referenceTarget = ReferenceTarget.ALL_VISIBLE
            layerOrder = listOf("color", "line")
            setRasterLayerSurfaces(mapOf("color" to PixelTileSurface(200, 200), "line" to border()))
            setProtectedLayerIds(setOf("line"))
            onSparseFillCommitted = { mask, _, _, _ -> committedWidth = mask.nonZeroBounds()?.width ?: 0 }
        }
        fun fill(time: Long): Int {
            send(view, time, MotionEvent.ACTION_DOWN, 100f, 100f); send(view, time + 50L, MotionEvent.ACTION_UP, 100f, 100f)
            return committedWidth
        }
        assertTrue("locked line art is referenced by default", fill(0L) in 80..120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeLockedLayersFromReference = true }
        assertEquals("excluding locked layers removes the boundary", 200, fill(100L))
        // The editing layer now carries the boundary; the other layer is empty.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.excludeLockedLayersFromReference = false; view.setProtectedLayerIds(emptySet())
            view.setRasterLayerSurfaces(mapOf("color" to border(), "line" to PixelTileSurface(200, 200)))
        }
        assertTrue("the editing layer is referenced by default", fill(200L) in 80..120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeActiveLayerFromReference = true }
        assertEquals("excluding the editing layer refers only to the others", 200, fill(300L))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.referenceTarget = ReferenceTarget.ACTIVE_LAYER }
        assertTrue("a current-layer reference ignores the editing-layer exclusion", fill(400L) in 80..120)
    }

    @Test fun referenceLayerFallbackNeverReintroducesAnExcludedEditingLayer() {
        var committedWidth = 0
        fun border() = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("border").use { w ->
                for (offset in -4..4) for (c in 50..150) {
                    w.setPixel(c, 50 + offset, Color.BLACK); w.setPixel(c, 150 + offset, Color.BLACK)
                    w.setPixel(50 + offset, c, Color.BLACK); w.setPixel(150 + offset, c, Color.BLACK)
                }
                w.commit().close()
            }
        }
        val view = canvas {
            tool = EditorTool.FILL; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
            pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = "color"
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS; referenceLayerIds = setOf("color")
            layerOrder = listOf("color", "line")
            setRasterLayerSurfaces(mapOf("color" to border(), "line" to PixelTileSurface(200, 200)))
            onSparseFillCommitted = { mask, _, _, _ -> committedWidth = mask.nonZeroBounds()?.width ?: 0 }
        }
        fun fill(time: Long): Int {
            send(view, time, MotionEvent.ACTION_DOWN, 100f, 100f); send(view, time + 50L, MotionEvent.ACTION_UP, 100f, 100f)
            return committedWidth
        }
        assertTrue("the editing layer is the only reference layer and bounds the fill", fill(0L) in 80..120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeActiveLayerFromReference = true }
        assertEquals("excluding it leaves nothing to refer to, so the fill covers the canvas", 200, fill(100L))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.referenceLayerIds = emptySet() }
        assertEquals("no marked reference layer: the fallback also respects the exclusion", 200, fill(200L))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeActiveLayerFromReference = false }
        assertTrue("without the exclusion the fallback still uses the editing layer", fill(300L) in 80..120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.excludeActiveLayerFromReference = true; view.referenceLayerIds = setOf("color", "line")
            view.setRasterLayerSurfaces(mapOf("color" to border(), "line" to border()))
        }
        assertTrue("other reference layers still apply when the editing layer is excluded", fill(400L) in 80..120)
    }

    private fun borderSurface() = PixelTileSurface(200, 200).also { surface ->
        surface.beginWrite("border").use { w ->
            for (offset in -4..4) for (c in 50..150) {
                w.setPixel(c, 50 + offset, Color.BLACK); w.setPixel(c, 150 + offset, Color.BLACK)
                w.setPixel(50 + offset, c, Color.BLACK); w.setPixel(150 + offset, c, Color.BLACK)
            }
            w.commit().close()
        }
    }
    private fun whiteSurface() = PixelTileSurface(200, 200).also { surface ->
        surface.beginWrite("white").use { w -> for (y in 0 until 200) for (x in 0 until 200) w.setPixel(x, y, Color.WHITE); w.commit().close() }
    }
    private fun fillWidth(view: InkCanvasView, time: Long, result: () -> Int): Int {
        send(view, time, MotionEvent.ACTION_DOWN, 100f, 100f); send(view, time + 50L, MotionEvent.ACTION_UP, 100f, 100f)
        return result()
    }

    @Test fun excludingAnEmptyLockedClippingBaseDoesNotReattachItsClippedLines() {
        var width = 0
        val view = canvas {
            tool = EditorTool.FILL; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
            pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = "color"
            referenceTarget = ReferenceTarget.ALL_VISIBLE
            layerOrder = listOf("color", "locked", "clip")
            setRasterLayerSurfaces(mapOf("color" to whiteSurface(), "locked" to PixelTileSurface(200, 200), "clip" to borderSurface()))
            setProtectedLayerIds(setOf("locked")); clippedLayers = setOf("clip")
            onSparseFillCommitted = { mask, _, _, _ -> width = mask.nonZeroBounds()?.width ?: 0 }
        }
        assertEquals("the empty clipping base hides the lines", 200, fillWidth(view, 0L) { width })
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeLockedLayersFromReference = true }
        assertEquals("excluding the base must not re-clip the lines to the layer below", 200, fillWidth(view, 100L) { width })
    }

    @Test fun excludedClippingBasesInsideFoldersBehaveAsHiddenAndKeepTheTree() {
        var width = 0
        val under = app.inkloom.document.InkLayer(id = "under", name = "Under")
        val color = app.inkloom.document.InkLayer(id = "color", name = "Color")
        val group = app.inkloom.document.InkLayer(id = "group", name = "Group", role = app.inkloom.document.LayerRole.FOLDER)
        val base = app.inkloom.document.InkLayer(id = "base", name = "Base", parentId = group.id)
        val clip = app.inkloom.document.InkLayer(id = "clip", name = "Clip", parentId = group.id, clippedToBelow = true)
        val view = canvas {
            tool = EditorTool.FILL; fingerDrawingEnabled = true; pagePaperColor = Color.WHITE
            pageModelWidth = 200f; pageModelHeight = 200f; activeLayerId = color.id
            referenceTarget = ReferenceTarget.ALL_VISIBLE
            // The opaque layer directly beneath the group would expose a wrong re-clip.
            setDocumentLayers(listOf(under, group, base, clip, color)); layerOrder = listOf("under", "base", "clip", "color")
            setRasterLayerSurfaces(mapOf("under" to whiteSurface(), "color" to PixelTileSurface(200, 200),
                "base" to whiteSurface(), "clip" to borderSurface()))
            clippedLayers = setOf("clip")
            bindDocumentSelectionState(app.inkloom.engine.v5.selection.DocumentSelectionRuntime(64).stateFor("reference-tree", "page", 200, 200))
            onSparseFillCommitted = { mask, _, _, _ -> width = mask.nonZeroBounds()?.width ?: 0 }
        }
        fun colorRange(): Boolean {
            var found = false
            // Clear the probe selection so the next fill uses the reference instead of the selection.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { found = view.selectColorRange(Color.BLACK, .05f, false); view.clearSelection() }
            return found
        }
        assertTrue("an opaque base shows its clipped lines", fillWidth(view, 0L) { width } in 80..120)
        assertTrue(colorRange())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.setProtectedLayerIds(setOf("base")); view.excludeLockedLayersFromReference = true }
        assertEquals("an excluded locked base hides the lines clipped to it", 200, fillWidth(view, 100L) { width })
        assertFalse("color range sees the same reference as fill", colorRange())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.excludeLockedLayersFromReference = false; view.setProtectedLayerIds(emptySet())
            view.setDocumentLayers(listOf(under, group, base.copy(draft = true), clip, color))
        }
        assertEquals("an excluded draft base hides its clipped lines too", 200, fillWidth(view, 200L) { width })
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.excludeDraftLayersFromReference = false }
        assertTrue("including drafts restores the clipped lines", fillWidth(view, 300L) { width } in 80..120)
    }

    @Test fun referenceTargetConfinesFillToMarkedLineArtLayer() {
        var committedWidth = 0f
        val color = PixelTileSurface(200, 200)
        val lineArt = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("V5 reference border").use { transaction ->
                for (offset in -4..4) {
                    for (coordinate in 50..150) {
                        transaction.setPixel(coordinate, 50 + offset, Color.BLACK)
                        transaction.setPixel(coordinate, 150 + offset, Color.BLACK)
                        transaction.setPixel(50 + offset, coordinate, Color.BLACK)
                        transaction.setPixel(150 + offset, coordinate, Color.BLACK)
                    }
                }
                transaction.commit().close()
            }
        }
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            pageModelWidth = 200f
            pageModelHeight = 200f
            activeLayerId = "color"
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS
            referenceLayerIds = setOf("line-art")
            layerOrder = listOf("color", "line-art")
            setRasterLayerSurfaces(mapOf("color" to color, "line-art" to lineArt))
            onSparseFillCommitted = { mask, _, _, _ ->
                committedWidth = mask.nonZeroBounds()?.width?.toFloat() ?: 0f
            }
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)

        assertTrue("V5 reference line art should keep the fill inside its boundary", committedWidth in 80f..120f)
        committedWidth = 0f
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.fillTargetMode = app.inkloom.settings.FillTargetMode.TRANSPARENT_ONLY
        }
        send(view, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 150L, MotionEvent.ACTION_UP, 100f, 100f)
        assertTrue("Transparent target with the same reference line art stays inside the boundary", committedWidth in 80f..120f)
    }

    @Test fun transparentFillIgnoresOpaquePaper() {
        var commits = 0
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            fillTargetMode = app.inkloom.settings.FillTargetMode.TRANSPARENT_ONLY
            onSparseFillCommitted = { mask, _, _, _ ->
                if (!mask.isEmpty) commits++
            }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals("Paper is excluded from transparent-target sampling", 1, commits)
    }

    @Test fun antiAliasedReferenceLineProducesPartialFillMaskWithoutCrossingOpaqueInk() {
        val color = PixelTileSurface(200, 200)
        val lineArt = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("antialiased line-art boundary").use { transaction ->
                for (y in 0 until 200) {
                    transaction.setPixel(100, y, Color.rgb(128, 128, 128))
                    transaction.setPixel(101, y, Color.BLACK)
                }
                transaction.commit().close()
            }
        }
        var edgeAlpha = -1
        var inkAlpha = -1
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            pageModelWidth = 200f
            pageModelHeight = 200f
            activeLayerId = "color"
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS
            referenceLayerIds = setOf("line-art")
            layerOrder = listOf("color", "line-art")
            setRasterLayerSurfaces(mapOf("color" to color, "line-art" to lineArt))
            onSparseFillCommitted = { mask, _, _, _ ->
                edgeAlpha = mask.alphaAt(100, 100)
                inkAlpha = mask.alphaAt(101, 100)
            }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 20f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 20f, 100f)
        assertTrue("Antialiased reference line receives partial edge coverage (was $edgeAlpha)", edgeAlpha in 1..254)
        assertEquals("Opaque contour must still block the fill", 0, inkAlpha)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.fillAntiAlias = false }
        send(view, 100L, MotionEvent.ACTION_DOWN, 20f, 100f)
        send(view, 150L, MotionEvent.ACTION_UP, 20f, 100f)
        assertEquals("Disabling antialiasing restores a hard boundary", 0, edgeAlpha)
    }

    @Test fun fillSelectionCommitsTheSelectionMaskInsteadOfFloodingAroundInk() {
        var committed = false
        val view = canvas {
            onSparseFillCommitted = { mask, _, _, _ ->
                committed = mask.logicalWidth > 1 && mask.logicalHeight > 1 &&
                    mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 0
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(view.selectAllEditableStrokes())
            assertTrue(view.fillSelection())
        }
        assertTrue("Selection launcher fill must commit the exact selection mask", committed)
    }

    @Test fun bucketTapWithActiveSelectionFillsTheWholeSelectionRegardlessOfSeed() {
        var committedMask: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
        val view = canvas {
            pageModelWidth = 200f
            pageModelHeight = 200f
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            selectionShape = app.inkloom.settings.SelectionShape.RECTANGLE
            onSparseFillCommitted = { mask, _, _, _ ->
                committedMask = mask
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.tool = EditorTool.SELECT
            sendDirect(view, 0L, MotionEvent.ACTION_DOWN, 50f, 50f)
            sendDirect(view, 20L, MotionEvent.ACTION_MOVE, 150f, 150f)
            sendDirect(view, 40L, MotionEvent.ACTION_UP, 150f, 150f)
            view.tool = EditorTool.FILL
            sendDirect(view, 60L, MotionEvent.ACTION_DOWN, 10f, 10f)
            sendDirect(view, 80L, MotionEvent.ACTION_UP, 10f, 10f)
        }
        val mask = requireNotNull(committedMask)
        assertEquals(200, mask.logicalWidth)
        assertEquals(200, mask.logicalHeight)
        assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 0)
        assertEquals(0, mask.alphaAt(mask.logicalWidth / 20, mask.logicalHeight / 20))
    }

    @Test fun committedBucketFillIsRemovedAndRestoredAsOneHistoryAction() {
        var visibleFills = 0
        lateinit var view: InkCanvasView
        view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            onSparseFillCommitted = { _, _, _, _ ->
                visibleFills++
                view.recordExternalEdit(undo = { visibleFills-- }, redo = { visibleFills++ })
            }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(1, visibleFills)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(0, visibleFills)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(1, visibleFills)
    }

    private fun sendDirect(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }
}
