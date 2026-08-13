package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeOpacityCompositorTest {
    @Test fun selfOverlapCannotExceedTheStrokeOpacityCap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
            }
            val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                val repeated = List(18) { index ->
                    StrokePoint(if (index % 2 == 0) .2f else .8f, .5f, pressure = 1f, timeMs = index.toLong())
                }
                view.drawActiveStrokes(
                    Canvas(output),
                    listOf(
                        InkStroke(
                            points = repeated,
                            colorArgb = Color.BLACK.toUInt().toLong(),
                            width = 80f,
                            opacity = .35f,
                            flow = 1f,
                            sizePressureEnabled = false,
                        ),
                    ),
                )

                val alpha = Color.alpha(output.getPixel(128, 128))
                assertTrue("Repeated passes produced alpha $alpha above the 35% stroke cap", alpha in 84..93)
            } finally {
                output.recycle()
            }
        }
    }
}
