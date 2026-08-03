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

    private fun writePng(file: File, pixels: IntArray, width: Int = 2, height: Int = 1) {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
