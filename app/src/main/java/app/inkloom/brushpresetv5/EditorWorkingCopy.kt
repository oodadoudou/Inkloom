package app.inkloom.brushpresetv5

/**
 * A single brush-edit session.
 *
 * Editing always flows:
 *
 * ```
 * Selected preset (factory or user)
 *          ↓
 * EditorWorkingCopy          ← transient, discarded on Cancel
 *          ↓
 * BrushPresetV5              ← the value being edited
 *          ↓ compile()
 * CompiledBrushProgramV5     ← immutable stroke-scoped V5 snapshot
 * ```
 *
 * The source preset is never mutated while the editor is open: every edit lands on
 * [current] (a detached copy). [cancel] simply drops the session; [commit] applies the edits
 * through the ownership rules (a factory preset cannot be saved in place — it must be saved as a
 * new user copy), and keeps the render identity + seed pinned exactly as the ownership
 * operations guarantee.
 *
 * A stroke already being drawn on the canvas keeps the [CompiledBrushProgramV5] it started with;
 * this session only ever produces new compiled snapshots for the Scratch Pad / next stroke.
 */
class EditorWorkingCopy private constructor(
    val source: OwnedBrushPreset,
    val current: BrushPresetV5,
) {
    val identity: PresetIdentity get() = current.identity
    val isEditingFactory: Boolean get() = source.isFactory

    /** Replaces the edited value wholesale (used when an option mutates a nested block). */
    fun update(transform: (BrushPresetV5) -> BrushPresetV5): EditorWorkingCopy =
        EditorWorkingCopy(source, transform(current))

    /** Convenience: swap in an explicit edited value. */
    fun withValue(edited: BrushPresetV5): EditorWorkingCopy = update { edited }

    /** The compiled snapshot for the current edited value (Scratch Pad / next stroke). */
    fun compiled(): CompiledBrushProgramV5 = CompiledBrushProgramV5.compile(current)

    /**
     * Discards the session; the source preset is returned unchanged. The UI can simply drop this
     * working copy — no per-field rollback is needed.
     */
    fun cancel(): OwnedBrushPreset = source

    /**
     * Applies the edits under the ownership rules:
     * - a factory source → Save As a new user copy (factory itself stays untouched);
     * - a user source → Save in place via [PresetOwnershipOperations.withEdits].
     */
    fun commit(newName: String? = null): OwnedBrushPreset {
        if (source.isFactory) {
            val name = newName ?: "${current.identity.name} - Modified"
            val edited = current.copy(identity = current.identity.copy(name = name))
            val savedAs = PresetOwnershipOperations.saveAs(source, name)
            return PresetOwnershipOperations.withEdits(savedAs, edited)
        }
        return PresetOwnershipOperations.withEdits(source, current)
    }

    /** Applies edits but keeps them in a fresh user copy without persisting to the source user
     * preset (Save As for a user preset). */
    fun commitAsNew(newName: String): OwnedBrushPreset {
        val edited = current.copy(identity = current.identity.copy(name = newName))
        val asNew = PresetOwnershipOperations.saveAs(source, newName)
        return PresetOwnershipOperations.withEdits(asNew, edited)
    }

    companion object {
        /** Starts an edit session from any owned preset. */
        fun start(source: OwnedBrushPreset): EditorWorkingCopy =
            EditorWorkingCopy(source, source.preset)

        /** Starts a from-scratch session (no source; saving creates a fresh user preset). */
        fun startFresh(preset: BrushPresetV5): EditorWorkingCopy {
            val owned = OwnedBrushPreset(
                preset = preset,
                ownership = PresetOwnership.User(userId = preset.identity.id, derivedFromFactoryId = null),
            )
            return EditorWorkingCopy(owned, preset)
        }
    }
}
