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
import app.inkloom.document.InkDocument
import app.inkloom.document.PageBackground
import app.inkloom.document.PageElement
import app.inkloom.document.SelectionMaskCompositor
import app.inkloom.document.ShapeKind
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.selection.SparseAlphaMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    @Test fun shapeMaskClipsGradientToTheSelectionRegion() {
        val mask = selectionMask(
            listOf(StrokePoint(.25f, .25f), StrokePoint(.75f, .25f), StrokePoint(.75f, .75f), StrokePoint(.25f, .75f)),
            featherPixels = 0f,
        )
        requireNotNull(mask)
        val maskPath = assets.saveFillMask(requireNotNull(SparseAlphaMask.fromBitmap(mask)).surface.snapshot())
        mask.recycle()
        val bitmap = render(element = element().copy(maskAssetPath = maskPath))
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel(200, 200))
            assertEquals(Color.WHITE, bitmap.getPixel(20, 20))
        } finally {
            bitmap.recycle()
            java.io.File(maskPath).delete()
        }
    }

    @Test fun sparseV5ShapeMaskPersistsAndClipsWithoutAFullSelectionBitmap() {
        val mask = MaskTileSurface(400, 400, tileSize = 128)
        mask.beginWrite("selection rectangle").run {
            composeAlpha(PixelRect(100, 100, 300, 300), ByteArray(200 * 200) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val maskPath = assets.saveFillMask(mask.snapshot())
        val restored = requireNotNull(assets.loadSparseFillMask(maskPath))
        assertEquals(9, restored.tileCount)
        val bitmap = render(element = element().copy(maskAssetPath = maskPath))
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel(200, 200))
            assertEquals(Color.WHITE, bitmap.getPixel(20, 20))
        } finally {
            bitmap.recycle()
            File(maskPath).delete()
        }
    }

    @Test fun maskedGradientSurvivesDocumentBundleRoundTrip() {
        val mask = selectionMask(
            listOf(StrokePoint(.2f, .2f), StrokePoint(.8f, .2f), StrokePoint(.8f, .8f), StrokePoint(.2f, .8f)),
            featherPixels = 12f,
        )
        val originalMaskPath = assets.saveFillMask(requireNotNull(SparseAlphaMask.fromBitmap(mask)).surface.snapshot())
        mask.recycle()
        val document = InkDocument(title = "Masked gradient", pages = listOf(InkPage(width = 400f, height = 400f, elements = listOf(element().copy(maskAssetPath = originalMaskPath)))))
        try {
            val bundle = ByteArrayOutputStream().also { assets.exportBundle(document, it) }.toByteArray()
            val restored = assets.importBundle(ByteArrayInputStream(bundle))
            val restoredGradient = restored.pages.single().elements.single() as PageElement.Shape
            val restoredMaskPath = requireNotNull(restoredGradient.maskAssetPath)
            assertTrue(File(restoredMaskPath).isFile)
            val rendered = assets.renderPageBitmap(restored.pages.single(), 400)
            try {
                assertNotEquals(Color.WHITE, rendered.getPixel(200, 200))
                assertEquals(Color.WHITE, rendered.getPixel(20, 20))
            } finally {
                rendered.recycle()
                File(restoredMaskPath).delete()
            }
        } finally {
            File(originalMaskPath).delete()
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

    private fun selectionMask(points: List<StrokePoint>, featherPixels: Float): Bitmap {
        val hardMask = requireNotNull(SelectionMaskCompositor.polygon(points, 400f, 400f))
        return try {
            requireNotNull(SelectionMaskCompositor.feather(hardMask, featherPixels, 400f))
        } finally {
            hardMask.recycle()
        }
    }

    private fun signature(bitmap: Bitmap): Long {
        var result = 17L
        for (y in 20 until 400 step 37) for (x in 20 until 400 step 31) result = result * 31L + bitmap.getPixel(x, y)
        return result
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }
}
