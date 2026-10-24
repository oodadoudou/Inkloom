package app.inkloom

import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** T16: the font and letter spacing of a text object reach exports, and a missing font falls back to the default. */
class TextFontRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(text: PageElement.Text): IntArray {
        val page = InkPage(width = 800f, height = 400f, elements = listOf(text))
        val bitmap = DocumentAssetService(context).renderPageBitmap(page, 400)
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    @Test fun fontAndLetterSpacingChangeTheRenderAndMissingFontsFallBack() {
        val plain = PageElement.Text(x = .05f, y = .1f, width = 700f, height = 300f, text = "Illustrated Wim", textSize = 64f)
        val default = pixels(plain)
        assertFalse("monospace draws differently", default.contentEquals(pixels(plain.copy(fontFamily = TextFonts.SYSTEM + "monospace"))))
        assertFalse("letter spacing spreads the text", default.contentEquals(pixels(plain.copy(letterSpacing = .3f))))
        assertArrayEquals("a missing imported font uses the default", default, pixels(plain.copy(fontFamily = TextFonts.IMPORTED + "not-here")))

        val system = File("/system/fonts").listFiles { file -> file.name.endsWith(".ttf") && file.length() in 10_000..5_000_000 }!!
            .first { runCatching { FontFileReader.read(it.readBytes()).familyName }.getOrNull() != null }
        val library = FontLibrary(context.filesDir)
        val font = library.add(system.nameWithoutExtension, system.readBytes())
        try {
            TextTypefaces.forget()
            assertNotNull(TextTypefaces.base(context.filesDir, TextFonts.IMPORTED + font.id))
            assertFalse(TextTypefaces.isMissing(context.filesDir, TextFonts.IMPORTED + font.id))
        } finally { library.remove(font.id); TextTypefaces.forget() }
    }
}
