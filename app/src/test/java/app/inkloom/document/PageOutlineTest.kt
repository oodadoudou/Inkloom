package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageOutlineTest {
    @Test fun ancestorsFollowTheCurrentBranchAcrossCollapsedAndSiblingEntries() {
        val pages = listOf(InkPage(id = "old"), InkPage(id = "old-child", outlineLevel = 1),
            InkPage(id = "root", outlineCollapsed = true), InkPage(id = "branch", outlineLevel = 1, outlineCollapsed = true),
            InkPage(id = "sibling", outlineLevel = 2), InkPage(id = "target", outlineLevel = 2))
        assertEquals(listOf("root", "branch"), PageOutline.ancestors(pages, "target").map(InkPage::id))
        assertEquals(emptyList<InkPage>(), PageOutline.ancestors(pages, "root"))
        assertEquals(emptyList<InkPage>(), PageOutline.ancestors(pages, "missing"))
    }

    @Test fun constrainsHierarchyToPreviousLevelPlusOne() {
        val pages = listOf(InkPage(id = "a"), InkPage(id = "b"), InkPage(id = "c"))
        val bIndented = PageOutline.changeLevel(pages, "b", 3)
        assertEquals(listOf(0, 1, 0), bIndented.map(InkPage::outlineLevel))
        val cIndented = PageOutline.changeLevel(bIndented, "c", 3)
        assertEquals(listOf(0, 1, 2), cIndented.map(InkPage::outlineLevel))
        assertEquals(0, PageOutline.changeLevel(cIndented, "a", 1).first().outlineLevel)
    }

    @Test fun collapsedParentHidesOnlyItsDescendants() {
        val pages = listOf(
            InkPage(id = "a", outlineCollapsed = true),
            InkPage(id = "b", outlineLevel = 1),
            InkPage(id = "c", outlineLevel = 2),
            InkPage(id = "d", outlineLevel = 0),
        )
        assertEquals(listOf("a", "d"), PageOutline.visiblePages(pages).map(InkPage::id))
        assertEquals(true, PageOutline.hasChildren(pages, 0))
        assertEquals(false, PageOutline.hasChildren(pages, 3))
    }
}
