package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.document.InkPage
import app.inkloom.document.PageBackground
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GradientRenderingTest {
    private val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
    private val stops = listOf(
        GradientColorStop(0f, 0xFFFF0000L),
        GradientColorStop(.42f, 0xFF00FF00L),
        GradientColorStop(1f, 0xFF0000FFL),
    )

    @Test fun allProfessionalGradientKindsRenderDistinctPageContent() {
        val signatures = GradientKind.entries.associateWith { kind ->
            render(kind = kind).useBitmap(::signature)
        }
        assertEquals(GradientKind.entries.size, signatures.values.toSet().size)
    }

    @Test fun legacyTwoColorGradientRendersLikeExplicitLinearGradient() {
        val legacy = element().copy(gradientStops = emptyList(), gradientKind = GradientKind.LINEAR)
        val explicit = element().copy(gradientStops = listOf(GradientColorStop(0f, 0xFFFF0000L), GradientColorStop(1f, 0xFF0000FFL)), gradientKind = GradientKind.LINEAR)
        val legacyBitmap = render(element = legacy)
        val explicitBitmap = render(element = explicit)
        try {
            listOf(20 to 200, 100 to 200, 200 to 200, 300 to 200, 380 to 200).forEach { (x, y) ->
                assertEquals(explicitBitmap.getPixel(x, y), legacyBitmap.getPixel(x, y))
            }
        } finally {
            legacyBitmap.recycle()
            explicitBitmap.recycle()
        }
    }

    @Test fun transparentEdgeLeavesThePageVisibleOutsideTheGradientRadius() {
        val bitmap = render(kind = GradientKind.RADIAL, edge = GradientEdgeMode.TRANSPARENT)
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel(200, 200))
            assertEquals(Color.WHITE, bitmap.getPixel(10, 10))
        } finally {
            bitmap.recycle()
        }
    }

    private fun element() = PageElement.Shape(
        x = 0f,
        y = 0f,
        width = 400f,
        height = 400f,
        kind = ShapeKind.RECTANGLE,
        fillArgb = 0xFFFF0000L,
        gradientEndArgb = 0xFF0000FFL,
        gradientStartX = .5f,
        gradientStartY = .5f,
        gradientEndX = .75f,
        gradientEndY = .5f,
        strokeWidth = 0f,
    )

    private fun render(kind: GradientKind = GradientKind.LINEAR, edge: GradientEdgeMode = GradientEdgeMode.CLAMP, element: PageElement.Shape? = null): Bitmap {
        val gradient = element ?: element().copy(gradientKind = kind, gradientEdgeMode = edge, gradientStops = stops)
        return assets.renderPageBitmap(InkPage(width = 400f, height = 400f, background = PageBackground.Paper(), elements = listOf(gradient)), 400)
    }

    private fun signature(bitmap: Bitmap): Long {
        var result = 17L
        for (y in 20 until 400 step 37) for (x in 20 until 400 step 31) result = result * 31L + bitmap.getPixel(x, y)
        return result
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }
}
