package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.*
import app.inkloom.engine.v5.material.*
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.BrushSourcePreview
import app.inkloom.ui.brushSourceCards
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BrushSourcePreviewTest {
    @Test fun importedShapeAndGrainPreviewUseTheProductionDecodedMask() = withSource { file ->
        val shape = BrushTipMaskRequest(file.path, .6f, alphaThresholdEnabled = true, alphaThreshold = .4f)
        val grain = GrainMaskRequest(file.path, brightness = .15f, contrast = .2f, reversed = true)
        for ((preview, mask) in listOf(
            BrushSourcePreview.Shape(shape) to requireNotNull(AndroidBrushTipMaskDecoder.decode(shape)),
            BrushSourcePreview.Grain(grain) to requireNotNull(AndroidBrushTipMaskDecoder.decodeGrain(grain)),
        )) {
            val bitmap = requireNotNull(preview.render())
            try {
                val level = mask.nativeLevel(0)
                assertEquals(level.width, bitmap.width); assertEquals(level.height, bitmap.height)
                assertArrayEquals(level.coverage.map { it.toInt() and 255 }.toIntArray(), alpha(bitmap))
            } finally { bitmap.recycle() }
        }
        val normal = requireNotNull(BrushSourcePreview.Grain(grain.copy(reversed = false)).render())
        val reversed = requireNotNull(BrushSourcePreview.Grain(grain).render())
        try { assertFalse(alpha(normal).contentEquals(alpha(reversed))) }
        finally { normal.recycle(); reversed.recycle() }
    }

    @Test fun multiTipOrderingAndSecondaryResourcesAreNotCollapsed() = withSource { file ->
        val preset = BrushPresetV5Import.requirePreset(SystemBrushCatalog.brushes.first { it.id == "system-hb-pencil" }).copy(
            tipTexture = TipTextureSettings(procedural = false, assetPaths = listOf(file.path, file.path + ".second")),
            textureGrain = TextureGrainSettings(file.path),
            dual = DualBrushSettings(listOf(file.path + ".secondary"), textureGrain = TextureGrainSettings(file.path + ".grain")),
        )
        val shapes = brushSourceCards(preset, BrushOptionGroup.TIP, UiLanguage.SIMPLIFIED_CHINESE)
        assertEquals(listOf("形状来源 1", "形状来源 2", "第二笔尖来源 1"), shapes.map { it.title })
        assertEquals(listOf(file.path, file.path + ".second", file.path + ".secondary"), shapes.map {
            (it.source as BrushSourcePreview.Shape).request.path
        })
        val grains = brushSourceCards(preset, BrushOptionGroup.PAPER, UiLanguage.SIMPLIFIED_CHINESE)
        assertTrue(grains.any { it.title == "颗粒来源" }); assertTrue(grains.any { it.title == "第二颗粒来源" })
    }

    @Test fun generatedGraphiteUsesItsRealSeedAndMissingResourcesStayExplicit() {
        val seed = 48135
        val source = BrushSourcePreview.Procedural(ProceduralTipKind.GRAPHITE, .7f, .8f, seed)
        val actual = requireNotNull(source.render())
        val different = requireNotNull(source.copy(seed = seed + 1).render())
        try {
            val expected = BrushTipMask.graphite(96, seed, .8f, .7f).nativeLevel(0)
            assertArrayEquals(expected.coverage.map { it.toInt() and 255 }.toIntArray(), alpha(actual))
            assertFalse(alpha(actual).contentEquals(alpha(different)))
        } finally { actual.recycle(); different.recycle() }
        assertNull(BrushSourcePreview.Shape(BrushTipMaskRequest("/missing/shape.png", 1f)).render())
        assertNull(BrushSourcePreview.Grain(GrainMaskRequest("/missing/grain.png")).render())
    }

    @Test fun largeSourcesRetainAspectWithinBoundedPreviewMemory() = withSource(1024, 512) { file ->
        val bitmap = requireNotNull(BrushSourcePreview.Grain(GrainMaskRequest(file.path)).render())
        try {
            assertEquals(2f, bitmap.width.toFloat() / bitmap.height, 0f)
            assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
        } finally { bitmap.recycle() }
    }

    private fun alpha(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in it.indices) it[index] = it[index] ushr 24
    }

    private fun withSource(width: Int = 32, height: Int = 16, action: (File) -> Unit) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "source-preview-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(IntArray(width * height) { index ->
                val value = (index * 73 + index / width * 17) % 256
                Color.rgb(value, value, value)
            }, 0, width, 0, 0, width, height)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            action(file)
        } finally { bitmap.recycle(); file.delete() }
    }
}
