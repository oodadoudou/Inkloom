package app.inkloom.document

import app.inkloom.settings.SelectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoSelectionPolicyTest {
    @Test fun replaceUsesOnlyTheNewLasso() {
        assertTrue(LassoSelectionPolicy.selected(SelectionMode.REPLACE, false, true))
        assertFalse(LassoSelectionPolicy.selected(SelectionMode.REPLACE, true, false))
    }

    @Test fun addUnionsOldAndNewSelection() {
        assertTrue(LassoSelectionPolicy.selected(SelectionMode.ADD, true, false))
        assertTrue(LassoSelectionPolicy.selected(SelectionMode.ADD, false, true))
    }

    @Test fun subtractKeepsOnlyPreviouslySelectedOutsidePieces() {
        assertFalse(LassoSelectionPolicy.selected(SelectionMode.SUBTRACT, true, true))
        assertTrue(LassoSelectionPolicy.selected(SelectionMode.SUBTRACT, true, false))
        assertFalse(LassoSelectionPolicy.selected(SelectionMode.SUBTRACT, false, false))
    }

    @Test fun intersectKeepsOnlyPreviouslySelectedInsidePieces() {
        assertTrue(LassoSelectionPolicy.selected(SelectionMode.INTERSECT, true, true))
        assertFalse(LassoSelectionPolicy.selected(SelectionMode.INTERSECT, false, true))
        assertFalse(LassoSelectionPolicy.selectedWithoutEligibleIntersection(SelectionMode.INTERSECT, true))
    }
}
