package app.inkloom.document

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.raster.PaintOpContext
import app.inkloom.engine.v5.raster.PublishablePaintOpSession
import app.inkloom.engine.v5.raster.RecordingPaintOp
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.VectorLayerRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VectorLayersTest {
    private val frame = VectorFrame(200, 100, pixelsPerUnit = 2f)

    /** A horizontal line across the frame at pixel row [y], [diameter] page units wide. */
    private fun line(y: Float = 50f, from: Float = 20f, to: Float = 180f, diameter: Float = 4f, color: Int = 0xFF2040C0.toInt()) =
        VectorStroke(points = listOf(from / 200f, y / 100f, 1f, to / 200f, y / 100f, 1f), colorArgb = color, diameter = diameter)

    private fun maxDifference(a: PixelTileSurfaceSnapshot, b: PixelTileSurfaceSnapshot): Int {
        var worst = 0
        for (y in 0 until a.logicalHeight) for (x in 0 until a.logicalWidth) {
            val p = a.pixelAt(x, y); val q = b.pixelAt(x, y)
            for (shift in intArrayOf(0, 8, 16, 24)) worst = maxOf(worst, abs((p ushr shift and 255) - (q ushr shift and 255)))
        }
        return worst
    }

    @Test fun replayMatchesTheLiveStroke() {
        val live = PixelTileSurface(200, 100)
        val prototype = VectorStroke(points = listOf(0f, 0f, 1f), colorArgb = 0xFF102030.toInt(), diameter = 5f, opacity = .8f, minimumPressureScale = .3f)
        val recorder = RecordingPaintOp(DryInkPaintOp(live, VectorLayerRenderer.definition(prototype, frame.pixelsPerUnit)))
        val session = recorder.begin(PaintOpContext(7L, live.revision, "test")) as PublishablePaintOpSession
        val path = (0..40).map { i -> StrokeSample(i.toLong(), DocumentPoint(15f + i * 4f, 30f + (i % 7) * 6f), i * 1000L, .2f + (i % 5) * .2f, 0f, 0f) }
        // Live input arrives in uneven batches; the replay sends one.
        path.chunked(6).forEachIndexed { index, batch -> session.apply(PaintBatch(7L, index.toLong(), batch, null, false)) }
        session.finish(PaintBatch.empty(7L, path.chunked(6).size.toLong(), true))
        session.publish().record.close()
        val stroke = prototype.copy(points = recorder.samples().flatMap { listOf(frame.pageX(it.point.x), frame.pageY(it.point.y), it.pressure) })
        assertEquals(path.size, stroke.pointCount)
        val replay = VectorLayerRenderer.render(VectorLayerContent(listOf(stroke)), frame)
        assertTrue("replay differs by at most rounding", maxDifference(live.snapshot(), replay.snapshot()) <= 1)
    }

    @Test fun regionRerenderMatchesAFullRender() {
        val before = VectorLayerContent(listOf(line(30f), line(60f, diameter = 8f), line(80f, color = 0xFFFF0000.toInt())))
        val target = VectorLayerRenderer.render(before, frame)
        val after = before.copy(strokes = before.strokes.filterIndexed { index, _ -> index != 1 })
        val record = VectorLayerRenderer.rerender(target, after, frame, PixelRect(0, 45, 200, 75))
        assertNotNull(record)
        assertEquals(0, maxDifference(target.snapshot(), VectorLayerRenderer.render(after, frame).snapshot()))
        target.apply(requireNotNull(record), PixelTileSurface.HistoryDirection.UNDO)
        assertEquals("undo restores the removed line", 0, maxDifference(target.snapshot(), VectorLayerRenderer.render(before, frame).snapshot()))
    }

    @Test fun touchedAreaEraserCutsTheLineInTwo() {
        val content = VectorLayerContent(listOf(line(), line(10f)))
        val erased = VectorGeometry.erase(content, listOf(100f, 40f, 100f, 60f), radius = 6f, VectorEraseMode.TOUCHED_AREA, frame)
        assertEquals("the untouched line stays as it was", content.strokes[1], erased.strokes.last())
        val pieces = erased.strokes.dropLast(1)
        assertEquals(2, pieces.size)
        assertEquals(content.strokes[0].id, pieces[0].id)
        val leftEnd = frame.pixelX(pieces[0].x(pieces[0].pointCount - 1)); val rightStart = frame.pixelX(pieces[1].x(0))
        assertTrue("left piece stops at the eraser: $leftEnd", leftEnd in 92f..95f)
        assertTrue("right piece resumes after it: $rightStart", rightStart in 105f..108f)
    }

    @Test fun wholeLineEraserRemovesTouchedLinesIncludingTheirWidth() {
        val content = VectorLayerContent(listOf(line(diameter = 10f), line(10f)))
        // The eraser passes 12 px below the centre line: outside it, but within the 10 px half width.
        val erased = VectorGeometry.erase(content, listOf(60f, 62f, 140f, 62f), radius = 3f, VectorEraseMode.WHOLE_LINE, frame)
        assertEquals(listOf(content.strokes[1]), erased.strokes)
        val missed = VectorGeometry.erase(content, listOf(60f, 75f, 140f, 75f), radius = 3f, VectorEraseMode.WHOLE_LINE, frame)
        assertEquals(content, missed)
    }

    @Test fun selectionPicksCrossingOrContainedLines() {
        val content = VectorLayerContent(listOf(line(from = 20f, to = 80f), line(70f, from = 20f, to = 180f), line(90f, from = 150f, to = 190f)))
        val leftHalf = { x: Float, _: Float -> x < 100f }
        assertEquals(setOf(content.strokes[0].id, content.strokes[1].id), VectorGeometry.select(content, frame, false, leftHalf))
        assertEquals(setOf(content.strokes[0].id), VectorGeometry.select(content, frame, true, leftHalf))
        val deleted = VectorGeometry.eraseWhere(content, frame, leftHalf)
        assertEquals("the line inside goes, the crossing line is cut, the outside line stays", 2, deleted.strokes.size)
        assertTrue(frame.pixelX(deleted.strokes[0].x(0)) >= 100f)
        assertEquals(content.strokes[2], deleted.strokes[1])
    }

    @Test fun croppedFramesMapPageFractions() {
        val cropped = VectorFrame(100, 100, 1f, PageCrop(.5f, 0f, 1f, 1f))
        assertEquals(0f, cropped.pixelX(.5f), 1e-4f)
        assertEquals(100f, cropped.pixelX(1f), 1e-4f)
        assertEquals(.75f, cropped.pageX(50f), 1e-4f)
        assertNull("a line in the cropped-away half is off the frame", VectorGeometry.bounds(line(from = 10f, to = 60f), cropped))
    }

    @Test fun rotationAndFlipsMoveTheLines() {
        val content = VectorLayerContent(listOf(VectorStroke(points = listOf(.1f, .2f, 1f), colorArgb = -1, diameter = 3f)))
        val rotated = VectorGeometry.rotatedClockwise(content).strokes.single()
        assertEquals(.8f, rotated.x(0), 1e-5f); assertEquals(.1f, rotated.y(0), 1e-5f)
        assertEquals(.9f, VectorGeometry.flipped(content, CanvasFlip.HORIZONTAL).strokes.single().x(0), 1e-5f)
        assertEquals(.8f, VectorGeometry.flipped(content, CanvasFlip.VERTICAL).strokes.single().y(0), 1e-5f)
        assertEquals(6f, VectorGeometry.mapped(content, 2f) { x, y -> x to y }.strokes.single().diameter, 1e-5f)
    }

    @Test fun svgDescribesEachLine() {
        val svg = VectorGeometry.svg(listOf(line(), VectorStroke(points = listOf(.5f, .5f, .5f), colorArgb = 0x80FF0000.toInt(), diameter = 2f, minimumPressureScale = .5f)), 200f, 100f)
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\" viewBox=\"0 0 200 100\">"))
        assertTrue(svg, svg.contains("<path d=\"M20 50 L180 50\" fill=\"none\" stroke=\"#2040c0\" stroke-width=\"4\""))
        assertTrue("dots get a zero-length segment and translucent colour", svg.contains("d=\"M100 50 L100 50\" fill=\"none\" stroke=\"#ff0000\" stroke-opacity=\"0.5\" stroke-width=\"1.5\""))
    }

    @Test fun vectorLayersSurviveSerialization() {
        val layer = InkLayer(id = "v", name = "Vector", vector = VectorLayerContent(listOf(line())))
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(layer, json.decodeFromString(InkLayer.serializer(), json.encodeToString(InkLayer.serializer(), layer)))
    }

    @Test fun pageEditsKeepLinesWithTheirPixels() {
        val page = InkPage(width = 200f, height = 100f, layers = listOf(InkLayer(id = LayerIds.INK, name = "Ink")))
        val insertion = requireNotNull(LayerInsertionEdit.create(page, LayerIds.INK, folder = false, vector = true))
        assertEquals("Vector 1", insertion.layer.name)
        val drawn = requireNotNull(insertion.apply(page)).let { created ->
            created.copy(layers = created.layers.map { if (it.id == insertion.layer.id) it.copy(vector = VectorLayerContent(listOf(line()))) else it })
        }
        val rotated = PageGeometry.rotateClockwise(drawn).layers.first { it.id == insertion.layer.id }.vector!!.strokes.single()
        assertEquals(.5f, rotated.x(0), 1e-5f); assertEquals(.1f, rotated.y(0), 1e-5f)
        assertEquals("clearing keeps an empty vector layer", VectorLayerContent(),
            RasterLayerDocumentOperations.clearUnlocked(drawn).layers.first { it.id == insertion.layer.id }.vector)
        assertNull("rasterizing drops the lines", GeneratedLayerEdits.rasterize(drawn, insertion.layer.id).after.layers.first { it.id == insertion.layer.id }.vector)
        assertEquals(LayerEditingTarget.Rejection.VECTOR, LayerEditingTarget.rejection(drawn.layers, insertion.layer.id))
    }
}
