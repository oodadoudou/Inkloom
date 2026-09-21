package app.inkloom.importer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BrushImportFailure(
    val id: String,
    val sourceUri: String,
    val displayName: String,
    val targetGroup: String,
    val reasons: List<String>,
    val finalStates: List<String>,
    val createdAt: Long,
    val lastAttemptAt: Long,
    val attemptCount: Int,
)

/** Device-private, bounded failure history. Source URIs are runtime data and never exported. */
class BrushImportFailureStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCES,
) {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<BrushImportFailure> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val sourceUri = item.optString("sourceUri")
                if (sourceUri.isBlank()) continue
                add(BrushImportFailure(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    sourceUri = sourceUri,
                    displayName = item.optString("displayName").ifBlank { "Brush import" },
                    targetGroup = item.optString("targetGroup"),
                    reasons = item.optJSONArray("reasons").strings(),
                    finalStates = item.optJSONArray("finalStates").strings(),
                    createdAt = item.optLong("createdAt", 0L),
                    lastAttemptAt = item.optLong("lastAttemptAt", 0L),
                    attemptCount = item.optInt("attemptCount", 1).coerceAtLeast(1),
                ))
            }
        }.sortedByDescending(BrushImportFailure::lastAttemptAt)
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(
        sourceUri: String,
        displayName: String,
        targetGroup: String,
        reasons: List<String>,
        finalStates: List<String>,
        attemptedAt: Long = System.currentTimeMillis(),
    ): BrushImportFailure {
        require(sourceUri.isNotBlank())
        val normalizedGroup = BrushGroupOperations.normalize(targetGroup)
        val current = load().toMutableList()
        val existingIndex = current.indexOfFirst { it.sourceUri == sourceUri && it.targetGroup == normalizedGroup }
        val existing = current.getOrNull(existingIndex)
        val updated = BrushImportFailure(
            id = existing?.id ?: UUID.randomUUID().toString(),
            sourceUri = sourceUri,
            displayName = displayName.ifBlank { existing?.displayName ?: "Brush import" },
            targetGroup = normalizedGroup,
            reasons = reasons.map(String::trim).filter(String::isNotEmpty).distinct().ifEmpty { listOf("Unknown import failure") }.take(MAX_REASONS),
            finalStates = finalStates.map(String::trim).filter(String::isNotEmpty).distinct(),
            createdAt = existing?.createdAt ?: attemptedAt,
            lastAttemptAt = attemptedAt,
            attemptCount = (existing?.attemptCount ?: 0) + 1,
        )
        if (existingIndex >= 0) current[existingIndex] = updated else current += updated
        save(current.sortedByDescending(BrushImportFailure::lastAttemptAt).take(MAX_ENTRIES))
        return updated
    }

    @Synchronized
    fun remove(id: String) = save(load().filterNot { it.id == id })

    @Synchronized
    fun removeSource(sourceUri: String, targetGroup: String) = save(load().filterNot {
        it.sourceUri == sourceUri && it.targetGroup == BrushGroupOperations.normalize(targetGroup)
    })

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(entries: List<BrushImportFailure>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("sourceUri", entry.sourceUri)
                put("displayName", entry.displayName)
                put("targetGroup", entry.targetGroup)
                put("reasons", JSONArray(entry.reasons))
                put("finalStates", JSONArray(entry.finalStates))
                put("createdAt", entry.createdAt)
                put("lastAttemptAt", entry.lastAttemptAt)
                put("attemptCount", entry.attemptCount)
            })
        }
        check(preferences.edit().putString(KEY, array.toString()).commit()) { "Could not save brush import failures" }
    }

    private fun JSONArray?.strings(): List<String> = buildList {
        val array = this@strings ?: return@buildList
        for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    companion object {
        const val MAX_ENTRIES = 100
        const val MAX_REASONS = 12
        private const val DEFAULT_PREFERENCES = "inkloom_brush_import_failures"
        private const val KEY = "failures"
    }
}
