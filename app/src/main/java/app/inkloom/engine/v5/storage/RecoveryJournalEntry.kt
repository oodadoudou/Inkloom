@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.inkloom.engine.v5.storage

import app.inkloom.document.PageElement
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Exact V5 recovery vocabulary.
 *
 * Paint pixels are durable through immutable sparse-surface manifests in document checkpoints;
 * the journal never serializes or reconstructs legacy vector strokes.
 */
@Serializable
internal sealed interface RecoveryJournalEntry {
    val documentId: String
    val pageId: String
    val timestamp: Long

    @Serializable
    @SerialName("element_upserted")
    data class ElementUpserted(
        override val documentId: String,
        override val pageId: String,
        val element: PageElement,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RecoveryJournalEntry

    /** A whole page after an edit (pen-up, layer change): durable before the notebook's checkpoint is rewritten. */
    @Serializable
    @SerialName("page_replaced")
    data class PageReplaced(
        override val documentId: String,
        override val pageId: String,
        val page: app.inkloom.document.InkPage,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RecoveryJournalEntry

    @Serializable
    @SerialName("element_removed")
    data class ElementRemoved(
        override val documentId: String,
        override val pageId: String,
        val elementId: String,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RecoveryJournalEntry
}
