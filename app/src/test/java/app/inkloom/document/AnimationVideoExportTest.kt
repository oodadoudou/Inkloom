package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationVideoExportTest {
    @Test fun videoSizesAreMultiplesOfSixteenAndKeepThePageShape() {
        assertEquals(720 to 1024, AnimationVideoExport.videoSize(720, 1200f, 1700f))
        assertEquals(480 to 272, AnimationVideoExport.videoSize(480, 1920f, 1080f))
        val (w, h) = AnimationVideoExport.videoSize(1080, 1000f, 1000f)
        assertEquals(0, w % 16); assertEquals(w, h)
    }

    @Test fun eachCelRepeatsForItsDurationInTimelineOrder() {
        val folder = InkLayer(id = "f", name = "Walk", role = LayerRole.FOLDER)
        val page = InkPage(layers = listOf(folder.copy(animation = AnimationFolder(cels = listOf(AnimationCel("a", 2), AnimationCel("gone", 3), AnimationCel("b", 1)), current = "a")),
            InkLayer(id = "a", name = "A", parentId = "f"), InkLayer(id = "b", name = "B", parentId = "f", visible = false)))
        assertEquals(listOf("a", "a", "b"), AnimationVideoExport.frameCels(page, "f"))
    }

    @Test fun yuvUsesVideoLevelsAndTransparencyShowsAsPaper() {
        val yuv = VideoFrames.i420(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0, 0xFF000000.toInt()), 2, 2)
        assertEquals(16, yuv[0].toInt() and 255); assertEquals(235, yuv[1].toInt() and 255)
        assertEquals(235, yuv[2].toInt() and 255)
        assertEquals(128, yuv[4].toInt() and 255); assertEquals(128, yuv[5].toInt() and 255)
        assertEquals(6, yuv.size)
    }
}
