package app.inkloom.document

import app.inkloom.settings.SelectionMode

object LassoSelectionPolicy {
    fun selected(mode: SelectionMode, previouslySelected: Boolean, insideLasso: Boolean): Boolean = when (mode) {
        SelectionMode.REPLACE -> insideLasso
        SelectionMode.ADD -> previouslySelected || insideLasso
        SelectionMode.SUBTRACT -> previouslySelected && !insideLasso
        SelectionMode.INTERSECT -> previouslySelected && insideLasso
    }

    fun selectedWithoutEligibleIntersection(mode: SelectionMode, previouslySelected: Boolean): Boolean = when (mode) {
        SelectionMode.REPLACE, SelectionMode.INTERSECT -> false
        SelectionMode.ADD, SelectionMode.SUBTRACT -> previouslySelected
    }
}
