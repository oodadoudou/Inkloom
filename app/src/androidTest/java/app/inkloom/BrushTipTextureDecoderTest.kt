package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrushTipTextureDecoderTest {
    @Test
    fun transparentWhiteAndBlackShapesUseTheSameAuthoredAlphaCoverage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-alpha-${System.nanoTime()}").apply { mkdirs() }
        try {
            val blackFile = File(root, "black.png")
            val whiteFile = File(root, "white.png")
            val alphas = IntArray(64) { index -> if (index % 8 in 2..5 && index / 8 in 2..5) 160 else 0 }
            writePng(blackFile, IntArray(64) { index -> Color.argb(alphas[index], 0, 0, 0) }, 8, 8)
            writePng(whiteFile, IntArray(64) { index -> Color.argb(alphas[index], 255, 255, 255) }, 8, 8)

            val black = requireNotNull(BrushTipTextureDecoder.decode(blackFile.absolutePath, 1f, null, 0f))
            val white = requireNotNull(BrushTipTextureDecoder.decode(whiteFile.absolutePath, 1f, null, 0f))
            try {
                assertEquals(Color.alpha(black.getPixel(3, 3)), Color.alpha(white.getPixel(3, 3)))
                assertEquals(160, Color.alpha(white.getPixel(3, 3)))
                assertEquals("Transparent margins must never become rectangular stamps", 0, Color.alpha(white.getPixel(0, 0)))
            } finally {
                black.recycle()
                white.recycle()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cspDualRgbDecodePreservesTransparentMaterialColorWithoutChangingDefaultMask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-rgb-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "colored-tip.png")
            val pixels = IntArray(64) { Color.TRANSPARENT }.also { values ->
                for (y in 2..5) for (x in 2..5) values[y * 8 + x] = Color.rgb(220, 35, 70)
            }
            writePng(tipFile, pixels, 8, 8)

            val alphaMask = requireNotNull(BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, null, 0f))
            val rgbMaterial = requireNotNull(BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, null, 0f, preserveRgb = true))
            try {
                val maskPixel = alphaMask.getPixel(3, 3)
                val rgbPixel = rgbMaterial.getPixel(3, 3)
                assertEquals(255, Color.red(maskPixel))
                assertEquals(255, Color.green(maskPixel))
                assertEquals(255, Color.blue(maskPixel))
                assertEquals(220, Color.red(rgbPixel))
                assertEquals(35, Color.green(rgbPixel))
                assertEquals(70, Color.blue(rgbPixel))
                assertEquals(255, Color.alpha(rgbPixel))
                assertEquals(0, Color.alpha(rgbMaterial.getPixel(0, 0)))
            } finally {
                alphaMask.recycle()
                rgbMaterial.recycle()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cspAntiAliasModesProduceFourDeterministicMaterialEdgeLevels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-aa-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            val pixels = IntArray(64) { Color.WHITE }.also { it[27] = Color.rgb(191, 191, 191) }
            writePng(tipFile, pixels, 8, 8)

            val alphas = listOf(CspAntiAlias.NONE, CspAntiAlias.WEAK, CspAntiAlias.MEDIUM, CspAntiAlias.STRONG).map { mode ->
                BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, null, 0f, antiAliasMode = mode)!!.let { bitmap ->
                    Color.alpha(bitmap.getPixel(3, 3)).also { bitmap.recycle() }
                }
            }

            assertEquals(listOf(0, 16, 64, 127), alphas)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sampledTipPreservesAuthoredGrayDensityRegardlessOfCircleHardness() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-density-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            val pixels = IntArray(64) { Color.WHITE }.also { it[27] = Color.rgb(191, 191, 191) }
            writePng(tipFile, pixels, 8, 8)

            val soft = BrushTipTextureDecoder.decode(tipFile.absolutePath, 0f, null, 0f)!!
            val hard = BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, null, 0f)!!

            assertEquals(64, Color.alpha(soft.getPixel(3, 3)))
            assertEquals(64, Color.alpha(hard.getPixel(3, 3)))
            soft.recycle()
            hard.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun procreateSizeTipIsBoundedBeforeEnteringTheLiveRenderCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-large-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "Shape.png")
            val source = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.WHITE)
            source.setPixel(1024, 1024, Color.BLACK)
            tipFile.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            source.recycle()

            val decoded = BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, null, 0f)

            assertNotNull(decoded)
            decoded!!
            assertEquals(512, maxOf(decoded.width, decoded.height))
            decoded.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nonPowerOfTwoTextureIsStrictlyBoundedWithoutChangingAspectRatio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-odd-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "Shape.png")
            val source = Bitmap.createBitmap(1500, 750, Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.BLACK)
            tipFile.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            source.recycle()

            val decoded = BrushTipTextureDecoder.decodeSampled(tipFile.absolutePath, 512)!!

            assertEquals(512, decoded.width)
            assertEquals(256, decoded.height)
            decoded.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blackRgbGrainUsesAlphaAsDensity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-texture-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            writePng(tipFile, intArrayOf(Color.BLACK, Color.BLACK))

            val grainFile = File(root, "grain.png")
            writePng(
                grainFile,
                intArrayOf(
                    Color.argb(0, 0, 0, 0),
                    Color.argb(255, 0, 0, 0),
                ),
            )

            val decoded = BrushTipTextureDecoder.decode(
                tipPath = tipFile.absolutePath,
                hardness = 1f,
                grainPath = grainFile.absolutePath,
                grainDensity = 1f,
            )

            assertNotNull(decoded)
            decoded!!
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
            assertEquals(255, Color.alpha(decoded.getPixel(1, 0)))
            decoded.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shapeMaskNormalizesBothLightAndDarkBackgroundConventions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-shape-convention-${System.nanoTime()}").apply { mkdirs() }
        try {
            val lightFile = File(root, "light.png")
            val darkFile = File(root, "dark.png")
            val lightPixels = IntArray(64) { Color.WHITE }.also { it[27] = Color.BLACK; it[28] = Color.BLACK; it[35] = Color.BLACK; it[36] = Color.BLACK }
            val darkPixels = IntArray(64) { Color.BLACK }.also { it[27] = Color.WHITE; it[28] = Color.WHITE; it[35] = Color.WHITE; it[36] = Color.WHITE }
            writePng(lightFile, lightPixels, 8, 8)
            writePng(darkFile, darkPixels, 8, 8)

            val light = BrushTipTextureDecoder.decode(lightFile.absolutePath, 1f, null, 0f)!!
            val dark = BrushTipTextureDecoder.decode(darkFile.absolutePath, 1f, null, 0f)!!

            assertEquals(0, Color.alpha(light.getPixel(0, 0)))
            assertEquals(255, Color.alpha(light.getPixel(3, 3)))
            assertEquals(0, Color.alpha(dark.getPixel(0, 0)))
            assertEquals(255, Color.alpha(dark.getPixel(3, 3)))
            light.recycle()
            dark.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun grainOriginOffsetChangesTheMaskWithoutChangingItsContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-grain-offset-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            writePng(tipFile, intArrayOf(Color.BLACK, Color.BLACK))
            val grainFile = File(root, "grain.png")
            writePng(grainFile, intArrayOf(Color.WHITE, Color.BLACK))

            val base = BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, grainFile.absolutePath, 1f)!!
            val shifted = BrushTipTextureDecoder.decode(tipFile.absolutePath, 1f, grainFile.absolutePath, 1f, grainOffsetX = .5f)!!

            assertEquals(Color.alpha(base.getPixel(0, 0)), Color.alpha(shifted.getPixel(1, 0)))
            assertEquals(Color.alpha(base.getPixel(1, 0)), Color.alpha(shifted.getPixel(0, 0)))
            base.recycle()
            shifted.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cspHeightTexturePreservesSolidTipCenterAndTexturesItsEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-height-texture-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            val tipPixels = IntArray(64) { Color.WHITE }.also {
                it[27] = Color.BLACK
                it[28] = Color.rgb(127, 127, 127)
            }
            writePng(tipFile, tipPixels, 8, 8)
            val grainFile = File(root, "grain.png")
            writePng(grainFile, IntArray(64) { Color.WHITE }, 8, 8)

            val height = BrushTipTextureDecoder.decode(
                tipPath = tipFile.absolutePath,
                hardness = 1f,
                grainPath = grainFile.absolutePath,
                grainDensity = 1f,
                grainCompositeMode = CspTextureComposite.HEIGHT,
            )!!

            assertEquals(255, Color.alpha(height.getPixel(3, 3)))
            assertEquals(0, Color.alpha(height.getPixel(4, 3)))
            height.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cspOutlineTextureUsesTheTextureContourInsteadOfItsFlatRegions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-outline-texture-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            writePng(tipFile, IntArray(64) { Color.rgb(127, 127, 127) }, 8, 8)
            val grainFile = File(root, "grain.png")
            writePng(grainFile, IntArray(64) { index -> if (index % 8 < 4) Color.WHITE else Color.BLACK }, 8, 8)

            val outline = BrushTipTextureDecoder.decode(
                tipPath = tipFile.absolutePath,
                hardness = 1f,
                grainPath = grainFile.absolutePath,
                grainDensity = 1f,
                grainCompositeMode = CspTextureComposite.OUTLINE,
            )!!

            assertEquals("Flat texture region must not become an outline", 0, Color.alpha(outline.getPixel(1, 3)))
            org.junit.Assert.assertTrue(
                "Texture transition must remain visible at the brush's soft density",
                kotlin.math.abs(Color.alpha(outline.getPixel(3, 3)) - 128) <= 2,
            )
            outline.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writePng(file: File, pixels: IntArray, width: Int = 2, height: Int = 1) {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
