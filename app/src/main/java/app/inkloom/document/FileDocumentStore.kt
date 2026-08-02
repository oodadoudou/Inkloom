package app.inkloom.document

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileDocumentStore(private val root: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val journalJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val documentsDir = File(root, "documents")
    private val journalFile = File(root, "journal.jsonl")

    init { documentsDir.mkdirs() }

    @Synchronized
    fun list(): List<InkDocument> = documentsDir.listFiles { file -> file.extension == "json" }
        ?.mapNotNull { runCatching { json.decodeFromString<InkDocument>(it.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        .orEmpty()

    fun get(id: String): InkDocument? = documentFile(id).takeIf(File::isFile)
        ?.let { runCatching { json.decodeFromString<InkDocument>(it.readText()) }.getOrNull() }

    @Synchronized fun save(document: InkDocument) = atomicWrite(documentFile(document.id), json.encodeToString(document))
    @Synchronized fun deletePermanently(id: String): Boolean = documentFile(id).delete()

    @Synchronized
    fun append(entry: JournalEntry) {
        journalFile.parentFile?.mkdirs()
        journalFile.appendText(journalJson.encodeToString<JournalEntry>(entry) + "\n")
        applyEntry(entry)?.let(::save)
    }

    @Synchronized
    fun recover(): Int {
        if (!journalFile.isFile) return 0
        val entries = journalFile.readLines().mapNotNull { runCatching { journalJson.decodeFromString<JournalEntry>(it) }.getOrNull() }
        entries.forEach { applyEntry(it)?.let(::save) }
        compactJournal()
        return entries.size
    }

    @Synchronized fun compactJournal() = atomicWrite(journalFile, "")

    private fun applyEntry(entry: JournalEntry): InkDocument? {
        val document = get(entry.documentId) ?: return null
        val pages = document.pages.map { page ->
            if (page.id != entry.pageId) page else when (entry) {
                is JournalEntry.StrokeAdded -> if (page.strokes.any { it.id == entry.stroke.id }) page else page.copy(strokes = page.strokes + entry.stroke)
                is JournalEntry.StrokeRemoved -> page.copy(strokes = page.strokes.filterNot { it.id == entry.strokeId })
                is JournalEntry.StrokesReplaced -> {
                    val replacedIds = entry.removedStrokeIds.toSet() + entry.strokes.map(InkStroke::id)
                    val remaining = page.strokes.filterNot { it.id in replacedIds }.toMutableList()
                    if (entry.replacementIndices.size == entry.strokes.size) {
                        entry.strokes.zip(entry.replacementIndices)
                            .sortedBy { (_, index) -> index }
                            .forEach { (stroke, index) -> remaining.add(index.coerceIn(0, remaining.size), stroke) }
                    } else {
                        remaining += entry.strokes
                    }
                    page.copy(strokes = remaining)
                }
                is JournalEntry.ElementUpserted -> page.copy(elements = page.elements.filterNot { it.id == entry.element.id } + entry.element)
                is JournalEntry.ElementRemoved -> page.copy(elements = page.elements.filterNot { it.id == entry.elementId })
            }
        }
        return document.copy(pages = pages, updatedAt = entry.timestamp)
    }

    private fun documentFile(id: String) = File(documentsDir, "${safeId(id)}.json")
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { error("Invalid document id") }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            target.writeText(content)
            temporary.delete()
        }
    }
}
