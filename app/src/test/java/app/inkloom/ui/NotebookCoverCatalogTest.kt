package app.inkloom.ui

import app.inkloom.document.CoverStyle
import app.inkloom.settings.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookCoverCatalogTest {
    @Test fun creationCatalogContainsEachRequestedPersonalCoverFamily() {
        assertEquals(
            listOf(CoverStyle.MINIMAL, CoverStyle.DARK, CoverStyle.PAPER, CoverStyle.GRID, CoverStyle.SOLID),
            NotebookCoverCatalog.creationStyles,
        )
        NotebookCoverCatalog.creationStyles.forEach { style ->
            assertTrue(NotebookCoverCatalog.displayName(style, UiLanguage.ENGLISH).isNotBlank())
            assertTrue(NotebookCoverCatalog.displayName(style, UiLanguage.SIMPLIFIED_CHINESE).isNotBlank())
        }
    }
}
