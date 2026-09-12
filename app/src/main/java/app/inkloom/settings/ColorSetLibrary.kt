package app.inkloom.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ColorSetSwatch(val id: String, val color: Int, val name: String = "")

@Serializable
data class NamedColorSet(
    val id: String,
    val name: String,
    val swatches: List<ColorSetSwatch> = emptyList(),
    val selectedSwatchId: String? = null,
) {
    val selectedSwatch: ColorSetSwatch? get() = swatches.firstOrNull { it.id == selectedSwatchId }
}

/** A swatch is an identity, not a unique color: equal colors may have different names. */
@Serializable
data class ColorSetLibrary(
    val sets: List<NamedColorSet> = emptyList(),
    val activeSetId: String? = null,
    val version: Int = 1,
) {
    val activeSet: NamedColorSet? get() = sets.firstOrNull { it.id == activeSetId }

    fun createSet(name: String, id: String = newId()): ColorSetLibrary {
        requireName(name)
        requireId(id)
        require(sets.none { it.id == id }) { "Color set ID already exists" }
        require(sets.size < MAX_SETS) { "Color set limit reached" }
        return copy(sets = sets + NamedColorSet(id, name.trim()), activeSetId = id)
    }

    fun renameSet(setId: String, name: String): ColorSetLibrary {
        requireName(name)
        return changeSet(setId) { it.copy(name = name.trim()) }
    }

    fun duplicateSet(setId: String, name: String, id: String = newId()): ColorSetLibrary {
        val source = requireSet(setId)
        require(totalSwatches + source.swatches.size <= MAX_TOTAL_SWATCHES) { "Total color limit reached" }
        val duplicate = source.swatches.map { it.copy(id = newId()) }
        val selectedIndex = source.swatches.indexOfFirst { it.id == source.selectedSwatchId }
        return createSet(name, id).changeSet(id) {
            it.copy(swatches = duplicate, selectedSwatchId = duplicate.getOrNull(selectedIndex)?.id)
        }
    }

    fun deleteSet(setId: String): ColorSetLibrary {
        val index = sets.indexOf(requireSet(setId))
        val remaining = sets.filterNot { it.id == setId }
        val nextActive = if (activeSetId == setId) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id else activeSetId
        return copy(sets = remaining, activeSetId = nextActive)
    }

    fun selectSet(setId: String): ColorSetLibrary {
        requireSet(setId)
        return copy(activeSetId = setId)
    }

    /** The index is the item's desired final position, after removal from its old position. */
    fun moveSet(setId: String, toIndex: Int): ColorSetLibrary {
        val source = requireSet(setId)
        require(toIndex in sets.indices) { "Color set destination is outside the list" }
        val reordered = sets.toMutableList().apply { remove(source); add(toIndex, source) }
        return copy(sets = reordered)
    }

    fun selectSwatch(setId: String, swatchId: String): ColorSetLibrary = changeSet(setId) { set ->
        requireSwatch(set, swatchId)
        set.copy(selectedSwatchId = swatchId)
    }.copy(activeSetId = setId)

    /** CSP Add inserts before the selected tile; an empty/unselected set appends. */
    fun insertSwatch(
        setId: String,
        color: Int,
        name: String = "",
        beforeIndex: Int? = null,
        id: String = newId(),
    ): ColorSetLibrary {
        requireName(name, allowEmpty = true)
        requireId(id)
        require(sets.none { set -> set.swatches.any { it.id == id } }) { "Color tile ID already exists" }
        require(totalSwatches < MAX_TOTAL_SWATCHES) { "Total color limit reached" }
        return changeSet(setId) { set ->
            require(set.swatches.size < MAX_SWATCHES_PER_SET) { "Color set tile limit reached" }
            val index = beforeIndex ?: set.swatches.indexOfFirst { it.id == set.selectedSwatchId }
                .takeIf { it >= 0 } ?: set.swatches.size
            require(index in 0..set.swatches.size) { "Color tile insertion is outside the list" }
            val swatches = set.swatches.toMutableList().apply { add(index, ColorSetSwatch(id, color, name.trim())) }
            set.copy(swatches = swatches, selectedSwatchId = id)
        }
    }

    fun replaceSwatch(setId: String, swatchId: String, color: Int): ColorSetLibrary = changeSet(setId) { set ->
        requireSwatch(set, swatchId)
        set.copy(swatches = set.swatches.map { if (it.id == swatchId) it.copy(color = color) else it })
    }

    fun renameSwatch(setId: String, swatchId: String, name: String): ColorSetLibrary {
        requireName(name, allowEmpty = true)
        return changeSet(setId) { set ->
            requireSwatch(set, swatchId)
            set.copy(swatches = set.swatches.map { if (it.id == swatchId) it.copy(name = name.trim()) else it })
        }
    }

    fun removeSwatch(setId: String, swatchId: String): ColorSetLibrary = changeSet(setId) { set ->
        val index = set.swatches.indexOf(requireSwatch(set, swatchId))
        val remaining = set.swatches.filterNot { it.id == swatchId }
        val selection = if (set.selectedSwatchId == swatchId) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id else set.selectedSwatchId
        set.copy(swatches = remaining, selectedSwatchId = selection)
    }

    /** Reordering never changes the selected tile, even when neighboring colors are identical. */
    fun moveSwatch(setId: String, swatchId: String, toIndex: Int): ColorSetLibrary = changeSet(setId) { set ->
        val swatch = requireSwatch(set, swatchId)
        require(toIndex in set.swatches.indices) { "Color tile destination is outside the list" }
        set.copy(swatches = set.swatches.toMutableList().apply { remove(swatch); add(toIndex, swatch) })
    }

    fun validated(): ColorSetLibrary = apply {
        require(version == 1) { "Unsupported color set library version" }
        require(sets.size <= MAX_SETS && totalSwatches <= MAX_TOTAL_SWATCHES) { "Color library limit exceeded" }
        require(sets.map { it.id }.distinct().size == sets.size) { "Duplicate color set IDs" }
        require((sets.isEmpty() && activeSetId == null) || sets.any { it.id == activeSetId }) { "Active color set is missing" }
        val swatchIds = HashSet<String>()
        sets.forEach { set ->
            requireId(set.id)
            requireName(set.name)
            require(set.swatches.size <= MAX_SWATCHES_PER_SET) { "Color set tile limit exceeded" }
            require(set.selectedSwatchId == null || set.swatches.any { it.id == set.selectedSwatchId }) { "Selected color tile is missing" }
            set.swatches.forEach { swatch ->
                requireId(swatch.id)
                requireName(swatch.name, allowEmpty = true)
                require(swatchIds.add(swatch.id)) { "Duplicate color tile IDs" }
            }
        }
    }

    private val totalSwatches: Int get() = sets.sumOf { it.swatches.size }
    private fun requireSet(id: String): NamedColorSet = requireNotNull(sets.firstOrNull { it.id == id }) { "Color set no longer exists" }
    private fun changeSet(id: String, change: (NamedColorSet) -> NamedColorSet): ColorSetLibrary {
        requireSet(id)
        return copy(sets = sets.map { if (it.id == id) change(it) else it })
    }

    companion object {
        const val MAX_SETS = 256
        const val MAX_SWATCHES_PER_SET = 4096
        const val MAX_TOTAL_SWATCHES = 16384
        const val MAX_NAME_LENGTH = 120

        /** Used only if the new library has never been created, including an empty old palette. */
        fun fromLegacy(colors: List<Int>, name: String): ColorSetLibrary {
            val setId = "migrated-colors"
            val swatches = colors.mapIndexed { index, color -> ColorSetSwatch("migrated-color-$index", color) }
            return ColorSetLibrary(listOf(NamedColorSet(setId, name.trim(), swatches)), setId).validated()
        }

        private fun newId(): String = UUID.randomUUID().toString()
        private fun requireId(id: String) = require(id.isNotBlank() && id.length <= 128) { "Invalid color item ID" }
        private fun requireName(name: String, allowEmpty: Boolean = false) = require(
            (allowEmpty || name.isNotBlank()) && name.length <= MAX_NAME_LENGTH && name.none { it == '\u0000' },
        ) { "Color name is empty or too long" }
        private fun requireSwatch(set: NamedColorSet, id: String): ColorSetSwatch =
            requireNotNull(set.swatches.firstOrNull { it.id == id }) { "Color tile no longer exists" }
    }
}

object ColorSetLibraryCodec {
    private const val MAX_CHARS = 16 * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(library: ColorSetLibrary): String = json.encodeToString(ColorSetLibrary.serializer(), library.validated()).also {
        require(it.length <= MAX_CHARS) { "Color library file is too large" }
    }

    /** Corrupt or unsupported data is explicit. Never silently replace it with the legacy palette. */
    fun decode(raw: String): ColorSetLibrary {
        require(raw.isNotBlank() && raw.length <= MAX_CHARS) { "Color library file is empty or too large" }
        return json.decodeFromString(ColorSetLibrary.serializer(), raw).validated()
    }
}

class ColorSetLibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE)

    fun load(defaultSetName: String = "Saved colors"): ColorSetLibrary = synchronized(lock) {
        val saved = preferences.getString(KEY, null)
        if (saved != null) ColorSetLibraryCodec.decode(saved)
        else ColorSetLibrary.fromLegacy(ColorPaletteCodec.decode(preferences.getString("colors", null)), defaultSetName).also(::save)
    }

    /** Reads the latest state, so two open palette views cannot overwrite each other's changes. */
    fun update(defaultSetName: String = "Saved colors", change: (ColorSetLibrary) -> ColorSetLibrary): ColorSetLibrary = synchronized(lock) {
        change(load(defaultSetName)).validated().also(::save)
    }

    fun replace(library: ColorSetLibrary): ColorSetLibrary = synchronized(lock) {
        library.validated().also(::save)
    }

    private fun save(library: ColorSetLibrary) {
        preferences.edit().putString(KEY, ColorSetLibraryCodec.encode(library)).apply()
    }

    companion object {
        const val KEY = "colorSetLibraryV1"
        private val lock = Any()
    }
}
