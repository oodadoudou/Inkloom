package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFileNameTest {
    @Test fun keepsReadableTitlesAndAddsTheExtension() {
        assertEquals("CS336 Reading Notes.pdf", ShareFileName.of("CS336 Reading Notes", "pdf"))
        assertEquals("读书笔记 第2页.png", ShareFileName.of("读书笔记 第2页", "png"))
    }

    @Test fun replacesCharactersOtherAppsCannotSave() {
        assertEquals("Q1_Q2 plan_ draft_.pdf", ShareFileName.of("Q1/Q2 plan: draft?", "pdf"))
        assertEquals("a_b.pdf", ShareFileName.of("a\u0000b", "pdf"))
    }

    @Test fun blankOrDotOnlyTitlesFallBack() {
        assertEquals("Inkloom.pdf", ShareFileName.of("   ", "pdf"))
        assertEquals("Inkloom.pdf", ShareFileName.of("..", "pdf"))
    }

    @Test fun longTitlesAreShortened() {
        val name = ShareFileName.of("x".repeat(300), "layers.zip")
        assertTrue(name.endsWith(".layers.zip"))
        assertTrue(name.length <= ShareFileName.MAX_BASE_LENGTH + ".layers.zip".length)
    }
}
