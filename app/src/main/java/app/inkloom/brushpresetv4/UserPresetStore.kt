package app.inkloom.brushpresetv4

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists user-owned [BrushPresetV4] presets (SharedPreferences + kotlinx JSON). Factory presets
 * are never stored here — they come from the catalog. Each stored record keeps its ownership
 * metadata ([PresetOwnership.User] + derived factory id) so Reset to Factory has a fixed
 * reference and the app can rebuild lineage after restart.
 */
class UserPresetStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkloom_user_presets", Context.MODE_PRIVATE)

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    private data class StoredUserPreset(
        val preset: BrushPresetV4,
        val derivedFromFactoryId: String?,
    )

    @kotlinx.serialization.Serializable
    private data class StoredRecord(
        val presetJson: String,
        val derivedFromFactoryId: String?,
    )

    fun load(): List<OwnedBrushPreset> {
        val raw = prefs.getString(KEY_USER_PRESETS, null) ?: return emptyList()
        return runCatching {
            val records = json.decodeFromString(ListSerializer(StoredRecord.serializer()), raw)
            records.mapNotNull { record ->
                runCatching {
                    val preset = json.decodeFromString(BrushPresetV4.serializer(), record.presetJson)
                    OwnedBrushPreset(
                        preset = preset,
                        ownership = PresetOwnership.User(
                            userId = preset.identity.id,
                            derivedFromFactoryId = record.derivedFromFactoryId,
                        ),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun save(presets: List<OwnedBrushPreset>) {
        val records = presets.map { owned ->
            StoredRecord(
                presetJson = json.encodeToString(BrushPresetV4.serializer(), owned.preset),
                derivedFromFactoryId = (owned.ownership as? PresetOwnership.User)?.derivedFromFactoryId,
            )
        }
        prefs.edit()
            .putString(KEY_USER_PRESETS, json.encodeToString(ListSerializer(StoredRecord.serializer()), records))
            .apply()
    }

    /** Upserts one preset (replaces by user id) and persists. */
    fun upsert(owned: OwnedBrushPreset): List<OwnedBrushPreset> {
        val all = load().toMutableList()
        val index = all.indexOfFirst { it.id == owned.id }
        if (index >= 0) all[index] = owned else all.add(owned)
        save(all)
        return all
    }

    fun delete(id: String): List<OwnedBrushPreset> {
        val all = load().filterNot { it.id == id }
        save(all)
        return all
    }

    private companion object {
        const val KEY_USER_PRESETS = "user_presets_v4"
    }
}
