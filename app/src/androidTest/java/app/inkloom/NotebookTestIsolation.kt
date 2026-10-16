package app.inkloom

import android.content.Context
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage

/** Start each UI scenario with its own notebook checkpoints, recovery journal and folder index. */
internal fun resetNotebookTestLibrary(context: Context) {
    check(context.filesDir.resolve("documents").deleteRecursively())
    context.filesDir.resolve("journal.jsonl").let { check(!it.exists() || it.delete()) }
    context.filesDir.resolve("library.json").let { check(!it.exists() || it.delete()) }
    seedTestNotebooks(context)
}

/**
 * The app no longer adds sample notebooks to an empty Library. UI scenarios were written against
 * these two notebooks, so the test process provides them instead.
 */
internal fun seedTestNotebooks(context: Context) {
    val store = FileDocumentStore(context.filesDir)
    if (store.list().isNotEmpty()) return
    listOf(
        InkDocument(title = "Distributed Systems", coverArgb = 0xFF202020, pages = List(3) { InkPage(title = "Page ${it + 1}") }),
        InkDocument(title = "CS336 Reading Notes", coverArgb = 0xFF707070, pages = List(2) { InkPage(title = "Page ${it + 1}") }),
    ).forEach(store::save)
}
