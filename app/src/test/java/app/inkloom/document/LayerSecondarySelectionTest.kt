package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerSecondarySelectionTest {
    @Test fun primaryIsIncludedInBatchAndLastSecondaryClearsSelection() {
        val selected = LayerSecondarySelection.toggle("active", emptySet(), "other")
        assertEquals(setOf("active", "other"), selected)
        assertEquals(emptySet<String>(), LayerSecondarySelection.toggle("active", selected, "other"))
    }
    @Test fun primaryCannotBeSwipedOutOfAnExistingBatch() {
        val selected = setOf("active", "one", "two")
        assertEquals(selected, LayerSecondarySelection.toggle("active", selected, "active"))
        assertEquals(setOf("active", "two"), LayerSecondarySelection.toggle("active", selected, "one"))
    }
}
