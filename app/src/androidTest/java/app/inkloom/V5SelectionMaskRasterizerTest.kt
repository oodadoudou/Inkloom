package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.selection.CanonicalLassoContour
import app.inkloom.engine.v5.selection.SelectionMaskRasterizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class V5SelectionMaskRasterizerTest {
    @Test fun boundedLassoProducesSparseAntialiasedEvenOddMask() {
        val contour = CanonicalLassoContour(
            points = listOf(
                DocumentPoint(100f, 100f), DocumentPoint(400f, 400f),
                DocumentPoint(100f, 400f), DocumentPoint(400f, 100f),
            ),
            closed = true,
        )

        val mask = SelectionMaskRasterizer.rasterize(contour, 1_000f, 1_000f, 2_000, 2_000)

        assertNotNull(mask)
        mask!!
        assertTrue(mask.tileCount < 64)
        assertTrue(mask.alphaAt(500, 300) > 0)
        assertEquals(0, mask.alphaAt(20, 20))
        val hasPartialCoverage = (190..210).any { y -> (190..210).any { x -> mask.alphaAt(x, y) in 1..254 } }
        assertTrue("anti-aliased edge must retain partial coverage", hasPartialCoverage)
    }

    @Test fun longContourRasterizationHasABoundedPenUpCost() {
        val contour = CanonicalLassoContour(
            points = List(720) { index ->
                val angle = 2.0 * PI * index / 720.0
                DocumentPoint(
                    800f + cos(angle).toFloat() * 680f,
                    1_131.5f + sin(angle).toFloat() * 980f,
                )
            },
            closed = true,
        )
        repeat(2) { SelectionMaskRasterizer.rasterize(contour, 1_600f, 2_263f, 1_600, 2_263) }
        val samples = LongArray(20) {
            val started = System.nanoTime()
            val mask = SelectionMaskRasterizer.rasterize(contour, 1_600f, 2_263f, 1_600, 2_263)
            assertNotNull(mask)
            System.nanoTime() - started
        }.sorted()
        val p95Millis = samples[p95Index(samples.size)] / 1_000_000.0

        println("V5 lasso 720-point 1600x2263 p95=${p95Millis}ms")
        assertTrue("final lasso mask p95 ${p95Millis}ms exceeded 150ms", p95Millis < 150.0)
    }
}
