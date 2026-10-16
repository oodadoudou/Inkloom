package app.inkloom.document

/**
 * Back and forward history of explicit page jumps (links, search, bookmarks, outline, the page
 * list), like a PDF reader's previous and next view. Ordinary previous/next page turns are not
 * jumps, so Back returns to where a jump started rather than to the neighbouring page. Entries are
 * stable page IDs: reordering keeps them valid and deleted pages are skipped.
 */
data class PageNavigationHistory(val back: List<String> = emptyList(), val forward: List<String> = emptyList()) {
    fun jumped(from: String, to: String): PageNavigationHistory {
        if (from == to) return this
        val nextBack = if (back.lastOrNull() == from) back else (back + from).takeLast(MAX_ENTRIES)
        return PageNavigationHistory(nextBack, emptyList())
    }

    fun backTarget(current: String, existing: Set<String>): String? = usable(back, current, existing).lastOrNull()
    fun forwardTarget(current: String, existing: Set<String>): String? = usable(forward, current, existing).lastOrNull()
    fun canGoBack(current: String, existing: Set<String>): Boolean = backTarget(current, existing) != null
    fun canGoForward(current: String, existing: Set<String>): Boolean = forwardTarget(current, existing) != null

    /** Returns the page to show and the history after stepping back from [current]. */
    fun goBack(current: String, existing: Set<String>): Pair<String, PageNavigationHistory>? {
        val stack = usable(back, current, existing)
        val target = stack.lastOrNull() ?: return null
        val nextForward = (usable(forward, current, existing) + current).takeLast(MAX_ENTRIES)
        return target to PageNavigationHistory(stack.dropLast(1), nextForward)
    }

    fun goForward(current: String, existing: Set<String>): Pair<String, PageNavigationHistory>? {
        val stack = usable(forward, current, existing)
        val target = stack.lastOrNull() ?: return null
        val nextBack = (usable(back, current, existing) + current).takeLast(MAX_ENTRIES)
        return target to PageNavigationHistory(nextBack, stack.dropLast(1))
    }

    fun retain(existing: Set<String>): PageNavigationHistory =
        PageNavigationHistory(collapse(back.filter { it in existing }), collapse(forward.filter { it in existing }))

    private fun usable(stack: List<String>, current: String, existing: Set<String>): List<String> =
        collapse(stack.filter { it in existing }).dropLastWhile { it == current }

    private fun collapse(ids: List<String>): List<String> =
        ids.filterIndexed { index, id -> index == 0 || ids[index - 1] != id }

    companion object {
        const val MAX_ENTRIES = 50
    }
}
