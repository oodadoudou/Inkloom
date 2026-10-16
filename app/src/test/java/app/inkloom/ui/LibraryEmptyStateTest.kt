package app.inkloom.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryEmptyStateTest {
    @Test fun searchExplainsAMissingMatchBeforeTheSection() {
        assertEquals(LibraryEmptyReason.NO_MATCHES, libraryEmptyReason(LibraryFilter.TRASH, inFolder = true, searching = true))
    }

    @Test fun eachSectionNamesWhatIsMissing() {
        assertEquals(LibraryEmptyReason.TRASH, libraryEmptyReason(LibraryFilter.TRASH, inFolder = false, searching = false))
        assertEquals(LibraryEmptyReason.ARCHIVED, libraryEmptyReason(LibraryFilter.ARCHIVED, inFolder = false, searching = false))
        assertEquals(LibraryEmptyReason.FAVORITES, libraryEmptyReason(LibraryFilter.FAVORITES, inFolder = true, searching = false))
        assertEquals(LibraryEmptyReason.PDF, libraryEmptyReason(LibraryFilter.PDF, inFolder = false, searching = false))
        assertEquals(LibraryEmptyReason.RECENT, libraryEmptyReason(LibraryFilter.RECENT, inFolder = false, searching = false))
    }

    @Test fun anEmptyFolderIsNotAnEmptyLibrary() {
        assertEquals(LibraryEmptyReason.FOLDER, libraryEmptyReason(LibraryFilter.ALL, inFolder = true, searching = false))
        assertEquals(LibraryEmptyReason.NO_NOTEBOOKS, libraryEmptyReason(LibraryFilter.ALL, inFolder = false, searching = false))
    }
}
