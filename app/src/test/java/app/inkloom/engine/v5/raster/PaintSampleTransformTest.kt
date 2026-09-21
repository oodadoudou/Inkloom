package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.stroke.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Test

class PaintSampleTransformTest {
    @Test fun radialRotationUsesDocumentPixelAspectAndRotatesBrushOrientation() {
        val transformed = PaintSampleTransform.rotation(
            angleRadians = (Math.PI / 2.0).toFloat(),
            centerX = 50f,
            centerY = 100f,
        ).apply(sample(75f, 100f, orientation = 0f))

        assertEquals(50f, transformed.point.x, .0001f)
        assertEquals(125f, transformed.point.y, .0001f)
        assertEquals((Math.PI / 2.0).toFloat(), transformed.orientationRadians, .0001f)
    }

    @Test fun reflectionKeepsContactDynamicsButMirrorsPointAndOrientation() {
        val source = sample(20f, 30f, orientation = .4f)
        val transformed = PaintSampleTransform.verticalAxis(100f).apply(source)

        assertEquals(80f, transformed.point.x, .0001f)
        assertEquals(30f, transformed.point.y, .0001f)
        assertEquals(source.pressure, transformed.pressure, 0f)
        assertEquals(source.tiltRadians, transformed.tiltRadians, 0f)
        assertEquals((Math.PI - .4).toFloat(), transformed.orientationRadians, .0001f)
    }

    @Test fun reflectionCanUseAnArbitraryOffsetAxis() {
        val horizontal = PaintSampleTransform.reflection(0f, centerX = 25f, centerY = 40f)
            .apply(sample(10f, 20f, orientation = 0f))
        assertEquals(10f, horizontal.point.x, .0001f)
        assertEquals(60f, horizontal.point.y, .0001f)

        val diagonal = PaintSampleTransform.reflection((Math.PI / 4.0).toFloat(), centerX = 0f, centerY = 0f)
            .apply(sample(20f, 0f, orientation = 0f))
        assertEquals(0f, diagonal.point.x, .0001f)
        assertEquals(20f, diagonal.point.y, .0001f)
    }

    private fun sample(x: Float, y: Float, orientation: Float) = StrokeSample(
        inputSequence = 1,
        point = DocumentPoint(x, y),
        eventTimeNanos = 1,
        pressure = .7f,
        tiltRadians = .3f,
        orientationRadians = orientation,
    )
}
