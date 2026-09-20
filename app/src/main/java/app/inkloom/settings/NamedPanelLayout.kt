package app.inkloom.settings

import kotlinx.serialization.Serializable

@Serializable
data class NamedPanelLayout(val name: String, val layout: EditorPanelLayout)

@Serializable
data class NamedPanelLayouts(val entries: List<NamedPanelLayout> = emptyList()) {
    fun save(name: String, layout: EditorPanelLayout): NamedPanelLayouts {
        val normalized = name.trim().take(40)
        if (normalized.isEmpty() || !EditorPanelLayouts.isValid(layout)) return this
        val withoutOld = entries.filterNot { it.name.equals(normalized, ignoreCase = true) }
        return copy(entries = (withoutOld + NamedPanelLayout(normalized, layout)).takeLast(20))
    }

    fun rename(oldName: String, newName: String): NamedPanelLayouts {
        val old = entries.firstOrNull { it.name == oldName } ?: return this
        val normalized = newName.trim().take(40)
        if (normalized.isEmpty() || entries.any { it.name.equals(normalized, ignoreCase = true) && it.name != oldName }) return this
        return copy(entries = entries.map { if (it.name == oldName) old.copy(name = normalized) else it })
    }

    fun delete(name: String): NamedPanelLayouts = copy(entries = entries.filterNot { it.name == name })
}
