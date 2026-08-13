package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.render.BrushProgramBatchRenderer
import app.inkloom.engine.v3.brush.ProceduralBrushTip
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushProgramBatchRendererTest {
    @Test
    fun batchedGraphitePreservesPerDabPressureOpacity() {
        val tip = requireNotNull(
            ProceduralBrushTip.create(
                InkStroke(
                    brushId = "system-hb-pencil",
                    hardness = .64f,
                    points = listOf(StrokePoint(.5f, .5f)),
                ),
            ),
        )
        val output = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
        try {
            val points = listOf(
                StrokePoint(.25f, .5f, pressure = .2f),
                StrokePoint(.75f, .5f, pressure = .9f),
            )
            val stroke = InkStroke(
                brushId = "system-hb-pencil",
                colorArgb = Color.BLACK.toUInt().toLong(),
                width = 120f,
                flow = 1f,
                sizePressureEnabled = false,
                opacityPressureEnabled = true,
                opacityPressureMinimum = .1f,
                points = points,
            )

            assertTrue(
                BrushProgramBatchRenderer.draw(
                    Canvas(output),
                    Paint(Paint.ANTI_ALIAS_FLAG),
                    stroke,
                    tip,
                    points,
                    0,
                    1000f,
                    300f,
                ),
            )
            val lightAlpha = Color.alpha(output.getPixel(250, 150))
            val heavyAlpha = Color.alpha(output.getPixel(750, 150))
            assertTrue(
                "Pressure alpha was not retained: light=$lightAlpha heavy=$heavyAlpha",
                heavyAlpha > lightAlpha * 2,
            )
        } finally {
            tip.recycle()
            output.recycle()
        }
    }
}
