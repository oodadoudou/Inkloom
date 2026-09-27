package app.inkloom.settings

/** Stable IDs allow a user's shortcut order to survive labels and app updates. */
enum class SelectionLauncherAction {
    ADD, SUBTRACT, INVERT, COPY, FEATHER, SAVED, QUICK_MASK, COLOR_FILL, CLEAR, FILL, TRANSFORM;
}

object SelectionLauncherLayout {
    val default: List<SelectionLauncherAction> = SelectionLauncherAction.entries.toList()
    fun decode(value: String?): List<SelectionLauncherAction> {
        val known = value.orEmpty().split(',').mapNotNull { token ->
            SelectionLauncherAction.entries.firstOrNull { it.name == token.trim() }
        }.distinct()
        return known + default.filterNot(known::contains)
    }
    fun encode(value: List<SelectionLauncherAction>): String =
        (value.distinct() + default.filterNot(value::contains)).joinToString(",") { it.name }

    fun move(value: List<SelectionLauncherAction>, action: SelectionLauncherAction, delta: Int): List<SelectionLauncherAction> {
        val next = decode(encode(value)).toMutableList()
        val from = next.indexOf(action)
        val to = from + delta
        if (from < 0 || to !in next.indices) return next
        next.removeAt(from)
        next.add(to, action)
        return next
    }
}
