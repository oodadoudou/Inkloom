package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v2.brush.EngineV2WetSmudgeRuntime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineV2WetSmudgeRuntimeTest {
    @Test fun depositedPigmentFeedsTheNextPickupSample() {
        val source = Bitmap.createBitmap(100, 24, Bitmap.Config.ARGB_8888)
        for (x in 0 until source.width) for (y in 0 until source.height) {
            source.setPixel(x, y, if (x < 50) Color.RED else Color.BLUE)
        }
        val runtime = EngineV2WetSmudgeRuntime(
            source = source,
            startX = .1f,
            startY = .5f,
            radiusPx = 3f,
            pickup = 1f,
            load = 0f,
            deposit = 1f,
            brushColor = Color.BLACK,
        )
        try {
            val pickedBlue = runtime.move(.9f, .5f, Color.BLACK)
            val feedbackPickup = runtime.move(.5f, .5f, Color.BLACK)

            assertTrue(Color.blue(pickedBlue) > Color.red(pickedBlue))
            assertTrue(
                "The second sample must observe the blue deposit written by the first segment",
                Color.blue(feedbackPickup) > Color.red(feedbackPickup),
            )
        } finally {
            runtime.close()
        }
    }
}
