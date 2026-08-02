package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextFormatterTest {
    @Test fun formatsCommonNotebookListsDeterministically() {
        assertEquals(listOf("• Alpha", "• Beta"), RichTextFormatter.lines("Alpha\nBeta", TextListStyle.BULLET))
        assertEquals(listOf("1. Alpha", "2. Beta"), RichTextFormatter.lines("Alpha\nBeta", TextListStyle.NUMBERED))
        assertEquals("☐ Alpha\n☐ Beta", RichTextFormatter.displayText("Alpha\nBeta", TextListStyle.CHECKBOX))
        assertEquals(listOf("Alpha", "Beta"), RichTextFormatter.lines("Alpha\nBeta", TextListStyle.NONE))
    }
}
