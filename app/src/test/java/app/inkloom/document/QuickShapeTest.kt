package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickShapeTest {
    private val pageWidth = 1000f
    private val pageHeight = 1000f
    private val color = 0xFF111111L
    private val width = 5f

    @Test fun openNearlyStraightPathSnapsToALine() {
        val points = (0..40).map { i ->
            val t = i / 40f
            ShapePoint(0.2f + 0.6f * t, 0.5f + 0.6f * t * 0.04f)
        }
        val shape = ShapeGeometry.quickShape(points, pageWidth, pageHeight, color, width)
        assertEquals(ShapeKind.LINE, shape?.kind)
    }

    @Test fun closedCircularPathBecomesAnEllipse() {
        val points = (0..60).map { i ->
            val angle = 2.0 * Math.PI * i / 60.0
            ShapePoint(0.5f + 0.2f * Math.cos(angle).toFloat(), 0.5f + 0.2f * Math.sin(angle).toFloat())
        }
        val shape = ShapeGeometry.quickShape(points, pageWidth, pageHeight, color, width)
        assertEquals(ShapeKind.ELLIPSE, shape?.kind)
    }

    @Test fun closedRectangularPathBecomesARectangle() {
        val perimeter = mutableListOf<ShapePoint>()
        fun edge(a: ShapePoint, b: ShapePoint, steps: Int) {
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                perimeter += ShapePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        val a = ShapePoint(0.2f, 0.2f); val b = ShapePoint(0.8f, 0.2f)
        val c = ShapePoint(0.8f, 0.8f); val d = ShapePoint(0.2f, 0.8f)
        edge(a, b, 20); edge(b, c, 20); edge(c, d, 20); edge(d, a, 20)
        val shape = ShapeGeometry.quickShape(perimeter, pageWidth, pageHeight, color, width)
        assertEquals(ShapeKind.RECTANGLE, shape?.kind)
    }

    @Test fun tinyAmbiguousPathIsNotRecognized() {
        val points = listOf(ShapePoint(0.5f, 0.5f), ShapePoint(0.501f, 0.501f), ShapePoint(0.5f, 0.5f))
        assertNull(ShapeGeometry.quickShape(points, pageWidth, pageHeight, color, width))
    }

    @Test fun openWavyPathIsLeftAsFreeform() {
        val points = (0..40).map { i ->
            val t = i / 40f
            ShapePoint(0.2f + 0.6f * t, 0.5f + 0.15f * Math.sin((t * 4 * Math.PI)).toFloat())
        }
        assertNull(ShapeGeometry.quickShape(points, pageWidth, pageHeight, color, width))
    }
}
