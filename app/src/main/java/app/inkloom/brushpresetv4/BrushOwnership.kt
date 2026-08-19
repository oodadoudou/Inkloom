package app.inkloom.brushpresetv4

import java.util.UUID

/**
 * Factory vs user preset ownership.
 *
 * A factory preset is immutable and always references the shipped default. Editing a factory preset
 * produces a detached user copy (Duplicate / Save As); Reset to Factory restores the factory
 * default; Delete removes a user copy only. Factory presets are never overwritten, so app upgrades
 * can ship new defaults without clobbering user edits.
 */
sealed interface PresetOwnership {
    /** A shipped default. Immutable. */
    data class Factory(val factoryId: String) : PresetOwnership

    /** A user-owned copy. May be derived from a factory default or created from scratch. */
    data class User(
        val userId: String,
        /** Factory this user copy was derived from, or null for a from-scratch user preset. */
        val derivedFromFactoryId: String?,
    ) : PresetOwnership
}

/** Owned preset: the value plus its ownership metadata. */
data class OwnedBrushPreset(
    val preset: BrushPresetV4,
    val ownership: PresetOwnership,
) {
    val id: String get() = when (ownership) {
        is PresetOwnership.Factory -> ownership.factoryId
        is PresetOwnership.User -> ownership.userId
    }
    val isFactory: Boolean get() = ownership is PresetOwnership.Factory
}

/**
 * Ownership operations. The operations are pure (return new values) so the caller controls when to
 * persist. Factory values are never mutated by these operations.
 */
object PresetOwnershipOperations {

    /** Duplicates any preset as a new user copy. Factory presets are never returned edited. */
    fun duplicate(owned: OwnedBrushPreset): OwnedBrushPreset {
        val userId = "user-" + UUID.randomUUID().toString()
        val factoryId = when (val o = owned.ownership) {
            is PresetOwnership.Factory -> o.factoryId
            is PresetOwnership.User -> o.derivedFromFactoryId
        }
        val copy = owned.preset.copy(
            identity = owned.preset.identity.copy(
                id = userId,
                name = "${owned.preset.identity.name} - Modified",
                version = owned.preset.identity.version + 1,
            ),
        )
        return OwnedBrushPreset(copy, PresetOwnership.User(userId, factoryId))
    }

    /** Save As: always creates a new user copy with a fresh id and an explicit name. */
    fun saveAs(owned: OwnedBrushPreset, newName: String): OwnedBrushPreset {
        val userId = "user-" + UUID.randomUUID().toString()
        val factoryId = when (val o = owned.ownership) {
            is PresetOwnership.Factory -> o.factoryId
            is PresetOwnership.User -> o.derivedFromFactoryId
        }
        val copy = owned.preset.copy(identity = owned.preset.identity.copy(id = userId, name = newName))
        return OwnedBrushPreset(copy, PresetOwnership.User(userId, factoryId))
    }

    /** Applies edits to an owned preset. A Factory preset cannot be edited in place; it must be
     * duplicated first (the editor always calls [duplicate] before showing edit controls). */
    fun withEdits(owned: OwnedBrushPreset, edits: BrushPresetV4): OwnedBrushPreset {
        require(!owned.isFactory) { "Factory presets are immutable; duplicate first." }
        val updated = edits.copy(identity = edits.identity.copy(id = owned.id))
        return OwnedBrushPreset(updated, owned.ownership)
    }

    /** Restores a user copy to its factory default, or returns null if there is no factory default. */
    fun resetToFactory(owned: OwnedBrushPreset, factoryDefault: BrushPresetV4?): OwnedBrushPreset? {
        if (owned.isFactory) return owned
        val factory = factoryDefault ?: return null
        val factoryId = (owned.ownership as PresetOwnership.User).derivedFromFactoryId
        return if (factoryId != null && factory.identity.id == factoryId) {
            OwnedBrushPreset(factory, PresetOwnership.Factory(factoryId))
        } else {
            null
        }
    }
}
