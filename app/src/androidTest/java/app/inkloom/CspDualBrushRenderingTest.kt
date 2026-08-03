package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.CspDualBrushBlendMode
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CspDualBrushRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun screenModeIsAppliedInsideTheDualStrokeBeforePageComposition() {
        val tip = File(context.cacheDir, "dual-screen-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF606060,
                width = 300f,
                opacity = 1f,
                flow = 1f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualSizeScale = 1f,
                dualFlow = 1f,
            )
            val service = DocumentAssetService(context)
            val normal = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(dualCompositeMode = 1))), 400)
            val screen = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(dualCompositeMode = 7))), 400)
            try {
                val normalPixel = normal.getPixel(200, normal.height / 2)
                val screenPixel = screen.getPixel(200, screen.height / 2)
                assertTrue("Screen must lighten the overlap of the two brush streams", Color.red(screenPixel) > Color.red(normalPixel))
                assertEquals(Color.red(screenPixel), Color.green(screenPixel))
                assertEquals(Color.green(screenPixel), Color.blue(screenPixel))
            } finally {
                normal.recycle()
                screen.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun unsupportedCspModesUseExplicitNormalFallback() {
        assertEquals(android.graphics.BlendMode.SRC_OVER, CspDualBrushBlendMode.resolve(4))
        assertEquals(android.graphics.BlendMode.SRC_OVER, CspDualBrushBlendMode.resolve(13))
    }
}
