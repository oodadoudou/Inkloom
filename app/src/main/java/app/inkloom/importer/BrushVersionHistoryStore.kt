package app.inkloom.importer

import android.content.Context
import app.inkloom.model.ImportedBrush

data class BrushVersion(
    val versionId: String,
    val savedAt: Long,
    val snapshot: ImportedBrush,
)

/**
 * Keeps bounded, full brush snapshots outside the main brush library. The
 * source package and extracted assets are referenced, never duplicated.
 */
class BrushVersionHistoryStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCES,
) {
    private val store = BrushMetadataStore(context, preferenceName, SNAPSHOT_KEY)

    @Synchronized
    fun versions(brushId: String): List<BrushVersion> = store.load()
        .mapNotNull(::decode)
        .filter { ownerOf(it.versionId) == brushId }
        .sortedWith(compareByDescending<BrushVersion> { it.savedAt }.thenByDescending { it.versionId })

    @Synchronized
    fun record(brush: ImportedBrush, savedAt: Long = System.currentTimeMillis()): BrushVersion {
        val canonicalBrush = BrushSemanticMapper.apply(brush)
        val all = store.load()
        val owned = all.mapNotNull(::decode)
            .filter { ownerOf(it.versionId) == brush.id }
            .sortedWith(compareByDescending<BrushVersion> { it.savedAt }.thenByDescending { it.versionId })
        owned.firstOrNull()?.takeIf { it.snapshot.copy(id = brush.id) == canonicalBrush }?.let { return it }

        var suffix = 0
        var versionId: String
        do {
            versionId = encodeId(brush.id, savedAt, suffix++)
        } while (all.any { it.id == versionId })
        val created = BrushVersion(versionId, savedAt, canonicalBrush.copy(id = versionId))
        val otherBrushes = all.filter { ownerOf(it.id) != brush.id }
        val retained = (listOf(created.snapshot) + owned.map(BrushVersion::snapshot)).take(MAX_VERSIONS_PER_BRUSH)
        store.save(otherBrushes + retained)
        return created
    }

    fun restore(current: ImportedBrush, version: BrushVersion): ImportedBrush = version.snapshot.copy(
        id = current.id,
        favorite = current.favorite,
        hidden = current.hidden,
        lastUsedAt = current.lastUsedAt,
    )

    @Synchronized
    fun delete(versionId: String) {
        store.save(store.load().filterNot { it.id == versionId })
    }

    @Synchronized
    fun clear(brushId: String) {
        store.save(store.load().filterNot { ownerOf(it.id) == brushId })
    }

    private fun decode(snapshot: ImportedBrush): BrushVersion? {
        val marker = snapshot.id.lastIndexOf(DELIMITER)
        if (marker <= 0) return null
        val encoded = snapshot.id.substring(marker + DELIMITER.length)
        val timestamp = encoded.substringBefore('-').toLongOrNull() ?: return null
        return BrushVersion(snapshot.id, timestamp, snapshot)
    }

    private fun ownerOf(versionId: String): String = versionId.substringBeforeLast(DELIMITER, missingDelimiterValue = "")

    private fun encodeId(brushId: String, savedAt: Long, suffix: Int): String = "$brushId$DELIMITER$savedAt-$suffix"

    companion object {
        const val MAX_VERSIONS_PER_BRUSH = 20
        private const val DEFAULT_PREFERENCES = "inkloom_brush_versions"
        private const val SNAPSHOT_KEY = "snapshots"
        private const val DELIMITER = "::inkloom-version::"
    }
}
